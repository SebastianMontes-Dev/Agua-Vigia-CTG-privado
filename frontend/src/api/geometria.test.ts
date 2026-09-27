import { IDBFactory } from 'fake-indexeddb'
import { almacenIndexedDb, cargarGeometria, VIGENCIA_GEOMETRIA_MS, type GeometriaSectores } from './geometria'

const GEOMETRIA: GeometriaSectores = {
  type: 'FeatureCollection',
  features: [
    { type: 'Feature', id: 'manga', properties: { nombre: 'MANGA' }, geometry: { type: 'Polygon', coordinates: [[[-75.53, 10.41], [-75.52, 10.41], [-75.52, 10.42], [-75.53, 10.41]]] } },
  ],
}

function responder(cuerpo: unknown, estado = 200) {
  return vi.fn<typeof fetch>(async () => new Response(JSON.stringify(cuerpo), { status: estado }))
}

describe('cargarGeometria', () => {
  beforeEach(() => { globalThis.indexedDB = new IDBFactory() })

  it('debePedirLaGeometriaSinAcceptJsonYGuardarlaEnIndexedDb', async () => {
    const pedir = responder(GEOMETRIA)
    const almacen = almacenIndexedDb()

    expect(await cargarGeometria(almacen, pedir, () => 1000)).toEqual(GEOMETRIA)
    expect(pedir).toHaveBeenCalledWith('/api/sectores/geometria')
    expect((await almacen.leer())?.guardadaEn).toBe(1000)
  })

  it('debeReutilizarLaCopiaGuardadaMientrasEsteVigente', async () => {
    const almacen = almacenIndexedDb()
    await almacen.guardar({ geometria: GEOMETRIA, guardadaEn: 0 })
    const pedir = responder({})

    expect(await cargarGeometria(almacen, pedir, () => VIGENCIA_GEOMETRIA_MS - 1)).toEqual(GEOMETRIA)
    expect(pedir).not.toHaveBeenCalled()
  })

  it('debeUsarLaCopiaVencidaSiLaRedFalla', async () => {
    const almacen = almacenIndexedDb()
    await almacen.guardar({ geometria: GEOMETRIA, guardadaEn: 0 })
    const pedir = vi.fn<typeof fetch>(async () => { throw new TypeError('sin red') })

    expect(await cargarGeometria(almacen, pedir, () => VIGENCIA_GEOMETRIA_MS * 2)).toEqual(GEOMETRIA)
    expect(pedir).toHaveBeenCalledOnce()
  })

  it('debeFallarSinCopiaYConUnaRespuestaQueNoEsGeometria', async () => {
    await expect(cargarGeometria(almacenIndexedDb(), responder({ type: 'Otra cosa' }))).rejects.toThrow('geometria inválida')
    await expect(cargarGeometria(almacenIndexedDb(), responder({}, 503))).rejects.toThrow('geometria 503')
  })
})
