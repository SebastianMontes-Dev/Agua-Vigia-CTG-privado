import { cerrarSesion, confirmarSegundoFactor, iniciarSesion, interpretarAlta, interpretarConfirmacionFactor, interpretarIngreso } from './panel'
import { crearCliente } from './cliente'
import { sesion } from './sesion'

const TIPO = 'https://aguavigia.example/errores/'

function respuesta(estado: number, cabeceras: Record<string, string> = {}) {
  return new Response(null, { status: estado, headers: cabeceras })
}

function clienteCon(pedir: ReturnType<typeof vi.fn<typeof fetch>>) {
  return crearCliente({ baseUrl: 'http://api.prueba', fetch: pedir })
}

function json(estado: number, cuerpo: unknown) {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': estado >= 400 ? 'application/problem+json' : 'application/json' },
  })
}

describe('interpretarIngreso', () => {
  it('debePedirElCodigoAnteSegundoFactorRequeridoSinTratarloComoClaveMala', () => {
    expect(interpretarIngreso(respuesta(401), { type: `${TIPO}segundo-factor-requerido` })).toEqual({ tipo: 'pide-codigo' })
    expect(interpretarIngreso(respuesta(401), { type: `${TIPO}credencial-invalida` })).toEqual({ tipo: 'credencial-invalida' })
  })

  it('debeLeerElEstadoDeLaCuentaNoHabilitada', () => {
    expect(interpretarIngreso(respuesta(403), { type: `${TIPO}cuenta-no-habilitada`, detail: 'No habilitada', estado: 'PENDIENTE_APROBACION' }))
      .toEqual({ tipo: 'no-habilitada', estado: 'PENDIENTE_APROBACION', mensaje: 'No habilitada' })
  })

  it('debeLeerLosSegundosDelBloqueoYElRetryAfterDelLimite', () => {
    expect(interpretarIngreso(respuesta(423), { type: `${TIPO}cuenta-bloqueada`, segundosRestantes: 840 }))
      .toEqual({ tipo: 'bloqueada', segundos: 840 })
    expect(interpretarIngreso(respuesta(429, { 'Retry-After': '120' }), { type: `${TIPO}limite-de-peticiones-excedido` }))
      .toEqual({ tipo: 'esperar', segundos: 120 })
  })

  it('debeTratarLaFaltaDeRespuestaComoSinRed', () => {
    expect(interpretarIngreso(null, null)).toEqual({ tipo: 'sin-red' })
  })
})

describe('iniciarSesion', () => {

  it('debeGuardarElTokenYElAlcanceRestringidoDeUnAdminSinTotp', async () => {
    const pedir = vi.fn<typeof fetch>().mockResolvedValue(json(200, { token: 't-restringido', alcance: 'ALTA_SEGUNDO_FACTOR', permisos: ['CONFIGURAR_SEGUNDO_FACTOR'] }))
    expect(await iniciarSesion('ana@example.com', 'una clave larga', undefined, clienteCon(pedir))).toEqual({ tipo: 'ingresado', alcance: 'ALTA_SEGUNDO_FACTOR' })
    expect(sesion.token()).toBe('t-restringido')
    expect(sesion.alcance()).toBe('ALTA_SEGUNDO_FACTOR')
  })

  it('debeOmitirElCodigoEnElPrimerIntentoYEnviarloEnElSegundo', async () => {
    const pedir = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(json(401, { type: `${TIPO}segundo-factor-requerido` }))
      .mockResolvedValueOnce(json(200, { token: 't', alcance: 'COMPLETO' }))
    expect(await iniciarSesion('ana@example.com', 'clave', undefined, clienteCon(pedir))).toEqual({ tipo: 'pide-codigo' })
    expect(await iniciarSesion('ana@example.com', 'clave', '123456', clienteCon(pedir))).toEqual({ tipo: 'ingresado', alcance: 'COMPLETO' })
    const cuerpos = await Promise.all(pedir.mock.calls.map(([peticion]) => (peticion as Request).clone().json() as Promise<Record<string, unknown>>))
    expect(cuerpos[0]).not.toHaveProperty('codigoTotp')
    expect(cuerpos[1]).toMatchObject({ codigoTotp: '123456' })
  })

  it('noDebeReintentarSoloAnteUnFalloDeRed', async () => {
    const pedir = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch'))
    expect(await iniciarSesion('ana@example.com', 'clave', undefined, clienteCon(pedir))).toEqual({ tipo: 'sin-red' })
    expect(pedir).toHaveBeenCalledTimes(1)
    expect(sesion.token()).toBeNull()
  })
})

describe('segundo factor', () => {

  it('debeDistinguirElCodigoIncorrectoDeLaSesionTerminada', () => {
    expect(interpretarAlta(respuesta(401), { type: `${TIPO}credencial-invalida` })).toEqual({ tipo: 'codigo-incorrecto' })
    expect(interpretarAlta(respuesta(401), { type: `${TIPO}sesion-sin-cuenta` })).toEqual({ tipo: 'sesion-terminada' })
    expect(interpretarAlta(respuesta(409), { type: `${TIPO}conflicto-de-estado` })).toEqual({ tipo: 'ya-activo' })
    expect(interpretarConfirmacionFactor(respuesta(409), { type: `${TIPO}conflicto-de-estado` })).toEqual({ tipo: 'sin-alta' })
  })

  it('noDebeDarPorBuenaUnaAltaSinSecreto', () => {
    expect(interpretarAlta(respuesta(200), { uri: 'otpauth://totp/x' })).toEqual({ tipo: 'fallo' })
  })

  it('debeReemplazarLaSesionRestringidaPorLaCompletaAlConfirmar', async () => {
    sesion.guardar('t-restringido', 'ALTA_SEGUNDO_FACTOR')
    const pedir = vi.fn<typeof fetch>().mockResolvedValue(json(200, { token: 't-completo', alcance: 'COMPLETO' }))
    expect(await confirmarSegundoFactor('123456', clienteCon(pedir))).toEqual({ tipo: 'activado' })
    expect(sesion.token()).toBe('t-completo')
    expect(sesion.alcance()).toBe('COMPLETO')
  })

  it('debeConservarLaSesionAnteUnCodigoIncorrecto', async () => {
    sesion.guardar('t-restringido', 'ALTA_SEGUNDO_FACTOR')
    const pedir = vi.fn<typeof fetch>().mockResolvedValue(json(401, { type: `${TIPO}credencial-invalida`, detail: 'El código no coincide.' }))
    expect(await confirmarSegundoFactor('000000', clienteCon(pedir))).toEqual({ tipo: 'codigo-incorrecto' })
    expect(sesion.token()).toBe('t-restringido')
  })
})

describe('cerrarSesion', () => {

  it('debeOlvidarElTokenAunqueFalleLaRed', async () => {
    sesion.guardar('vigente')
    await cerrarSesion(clienteCon(vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch'))))
    expect(sesion.token()).toBeNull()
    expect(sesion.alcance()).toBeNull()
  })
})
