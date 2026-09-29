import { afterEach, describe, expect, it, vi } from 'vitest'
import { normalizarError } from './cliente'
import { interpretarIngreso, mensajeDeEstadoDeCuenta, validarClaveNueva } from './panel'

const PREFIJO = 'https://aguavigia.example/errores/'

function problema(slug: string, estado: number, extra: Record<string, unknown> = {}) {
  return new Response(JSON.stringify({ type: PREFIJO + slug, title: slug, status: estado, detail: `Detalle de ${slug}`, ...extra }), {
    status: estado, headers: { 'Content-Type': 'application/problem+json' },
  })
}

async function errorDe(respuesta: Response) {
  return normalizarError(respuesta, await respuesta.clone().json())
}

function json(cuerpo: unknown, estado = 200) {
  return new Response(JSON.stringify(cuerpo), { status: estado, headers: { 'Content-Type': 'application/json' } })
}

/** El cliente `api` se crea con la red real: se sustituye por uno con la red simulada y se importan de nuevo los módulos. */
async function conRed(respuesta: Response | Error) {
  const simulado = vi.fn<typeof fetch>(() => respuesta instanceof Error ? Promise.reject(respuesta) : Promise.resolve(respuesta))
  vi.resetModules()
  vi.doMock('./cliente', async (original) => {
    const real = await original<typeof import('./cliente')>()
    return { ...real, api: real.crearCliente({ baseUrl: 'http://servidor.prueba', fetch: simulado }) }
  })
  const panel = await import('./panel')
  const { sesion } = await import('./sesion')
  return { red: simulado, panel, sesion }
}

afterEach(() => { vi.doUnmock('./cliente') })

describe('interpretarIngreso', () => {
  it('debePedirElCodigoSinTratarloComoClaveMala', async () => {
    expect(interpretarIngreso(await errorDe(problema('segundo-factor-requerido', 401)))).toEqual({ tipo: 'pide-codigo' })
  })

  it('noDebeDistinguirSiFalloElCorreoOLaClave', async () => {
    expect(interpretarIngreso(await errorDe(problema('credencial-invalida', 401)))).toEqual({ tipo: 'credencial-invalida' })
  })

  it('debeExplicarElEstadoDeLaCuentaQueNoPuedeEntrar', async () => {
    const resultado = interpretarIngreso(await errorDe(problema('cuenta-no-habilitada', 403, { estado: 'PENDIENTE_APROBACION' })))
    expect(resultado).toEqual({ tipo: 'cuenta-no-habilitada', mensaje: mensajeDeEstadoDeCuenta('PENDIENTE_APROBACION') })
    expect(mensajeDeEstadoDeCuenta('PENDIENTE_APROBACION')).toMatch(/administrador/)
  })

  it('debeLeerLosSegundosRestantesDeUnaCuentaBloqueada', async () => {
    expect(interpretarIngreso(await errorDe(problema('cuenta-bloqueada', 423, { segundosRestantes: 600 }))))
      .toEqual({ tipo: 'bloqueada', segundos: 600 })
  })

  it('debeRespetarElRetryAfterDelLimitePorIp', () => {
    const respuesta = new Response(null, { status: 429, headers: { 'Retry-After': '120' } })
    expect(interpretarIngreso(normalizarError(respuesta, null))).toEqual({ tipo: 'esperar', segundos: 120 })
  })

  it('debeDistinguirLaFaltaDeRedDeUnFalloDelServidor', () => {
    expect(interpretarIngreso(normalizarError(null, null))).toEqual({ tipo: 'sin-red' })
    expect(interpretarIngreso(normalizarError(new Response(null, { status: 500 }), null))).toEqual({ tipo: 'fallo' })
  })
})

describe('iniciarSesion', () => {
  it('debeGuardarElTokenYElAlcanceDeUnaSesionCompleta', async () => {
    const { red, panel, sesion } = await conRed(json({ token: 'jwt-1', alcance: 'COMPLETO' }))
    expect(await panel.iniciarSesion('ana@example.com', 'una clave larga y unica')).toEqual({ tipo: 'entro', alcance: 'COMPLETO' })
    expect(sesion.token()).toBe('jwt-1')
    expect(sesion.alcance()).toBe('COMPLETO')
    const peticion = red.mock.calls[0]?.[0] as Request
    expect(await peticion.clone().json()).toEqual({ correo: 'ana@example.com', clave: 'una clave larga y unica' })
  })

  it('debeRecordarQueUnAdminSinSegundoFactorSoloTieneElAlcanceDeAlta', async () => {
    const { panel, sesion } = await conRed(json({ token: 'jwt-2', alcance: 'ALTA_SEGUNDO_FACTOR' }))
    expect(await panel.iniciarSesion('admin@example.com', 'una clave larga y unica')).toEqual({ tipo: 'entro', alcance: 'ALTA_SEGUNDO_FACTOR' })
    expect(sesion.alcance()).toBe('ALTA_SEGUNDO_FACTOR')
  })

  it('debeEnviarElCodigoTotpSoloCuandoLoHay', async () => {
    const { red, panel, sesion } = await conRed(problema('segundo-factor-requerido', 401))
    expect(await panel.iniciarSesion('ana@example.com', 'una clave larga y unica', '123456')).toEqual({ tipo: 'pide-codigo' })
    const peticion = red.mock.calls[0]?.[0] as Request
    expect(await peticion.clone().json()).toMatchObject({ codigoTotp: '123456' })
    expect(sesion.token()).toBeNull()
  })

  it('noDebeGuardarNadaSiHayFalloDeRed', async () => {
    const { panel, sesion } = await conRed(new Error('sin red'))
    expect(await panel.iniciarSesion('ana@example.com', 'una clave larga y unica')).toEqual({ tipo: 'sin-red' })
    expect(sesion.token()).toBeNull()
  })
})

describe('cambiarClave', () => {
  it('debeDescartarLaSesionPorqueElServidorLaCierraTambien', async () => {
    const { panel, sesion } = await conRed(new Response(null, { status: 204 }))
    sesion.guardar('jwt-3')
    expect((await panel.cambiarClave('una clave larga y unica', 'otra clave larga y distinta')).ok).toBe(true)
    expect(sesion.token()).toBeNull()
  })

  it('debeConservarLaSesionSiLaClaveDeHoyNoEsCorrecta', async () => {
    const { panel, sesion } = await conRed(problema('peticion-invalida', 400))
    sesion.guardar('jwt-4')
    expect((await panel.cambiarClave('mala', 'otra clave larga y distinta')).ok).toBe(false)
    expect(sesion.token()).toBe('jwt-4')
  })
})

describe('validarClaveNueva', () => {
  it.each([
    ['corta', /12 caracteres/],
    ['a'.repeat(129), /128/],
    ['ñ'.repeat(40), /72 bytes/],
    ['aaaaaaaaaaaaaaaa', /repite/],
    [' una clave larga y unica', /espacios/],
  ])('debeRechazar %s', (clave, mensaje) => {
    expect(validarClaveNueva(clave)).toMatch(mensaje)
  })

  it('debeAceptarUnaClaveQueCumpleLaPolitica', () => {
    expect(validarClaveNueva('una clave larga y unica')).toBeNull()
  })
})
