import { enviarReporte, interpretarEnvio, interpretarFoto } from './reportes'

const TIPO = 'https://aguavigia.example/errores/'

function respuesta(estado: number, cabeceras: Record<string, string> = {}) {
  return new Response(null, { status: estado, headers: cabeceras })
}

describe('interpretarEnvio', () => {
  it('debeReconocerElReporteRecibido', () => {
    expect(interpretarEnvio(respuesta(201), { id: 'r1', sectorId: 'manga' })).toEqual({ tipo: 'recibido', reporte: { id: 'r1', sectorId: 'manga' } })
  })

  it('noDebeDarPorRecibidoUnEnvioSinRespuesta', () => {
    expect(interpretarEnvio(null, null)).toEqual({ tipo: 'incierto' })
  })

  it('debeDistinguirElCupoDelSectorDelLimitePorIp', () => {
    expect(interpretarEnvio(respuesta(429), { type: `${TIPO}limite-reportes-excedido` })).toEqual({ tipo: 'cupo-agotado' })
    expect(interpretarEnvio(respuesta(429, { 'Retry-After': '20' }), { type: `${TIPO}limite-de-peticiones-excedido` }))
      .toEqual({ tipo: 'esperar', segundos: 20 })
  })

  it('debeMostrarElDetalleDeUnRechazoYOcultarElDeUnFalloInterno', () => {
    expect(interpretarEnvio(respuesta(400), { type: `${TIPO}peticion-invalida`, detail: 'No existe el sector' }))
      .toEqual({ tipo: 'rechazado', mensaje: 'No existe el sector' })
    expect(interpretarEnvio(respuesta(500), { type: `${TIPO}error-interno`, detail: 'NullPointer' })).toEqual({ tipo: 'fallo' })
  })
})

describe('enviarReporte', () => {
  it('noDebeIntentarElEnvioSinConexion', async () => {
    const pedir = vi.spyOn(globalThis, 'fetch')
    expect(await enviarReporte('manga', 'SIN_AGUA', () => false)).toEqual({ tipo: 'sin-red' })
    expect(pedir).not.toHaveBeenCalled()
  })
})

describe('interpretarFoto', () => {
  it('debeExplicarCadaRechazoDeLaFoto', () => {
    expect(interpretarFoto(respuesta(200), {})).toEqual({ tipo: 'recibida' })
    expect(interpretarFoto(respuesta(413), {})).toEqual({ tipo: 'muy-grande' })
    expect(interpretarFoto(respuesta(400), { type: `${TIPO}peticion-invalida`, detail: 'Firma inválida' }))
      .toEqual({ tipo: 'no-valida', mensaje: 'Firma inválida' })
    expect(interpretarFoto(null, null)).toEqual({ tipo: 'fallo' })
  })
})
