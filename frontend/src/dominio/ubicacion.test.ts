import type { GeometriaSectores } from '../api/geometria'
import { sectorEnCoordenada } from './ubicacion'

function cuadro(lon: number, lat: number, lado: number) {
  return [[lon, lat], [lon + lado, lat], [lon + lado, lat + lado], [lon, lat + lado], [lon, lat]]
}

const GEOMETRIA: GeometriaSectores = {
  type: 'FeatureCollection',
  features: [
    // Manga con un hueco en el centro: lo que cae en el hueco no es Manga.
    { type: 'Feature', id: 'manga', properties: {}, geometry: { type: 'Polygon', coordinates: [cuadro(-75.54, 10.40, 0.02), cuadro(-75.535, 10.405, 0.01)] } },
    { type: 'Feature', id: 'islas', properties: {}, geometry: { type: 'MultiPolygon', coordinates: [[cuadro(-75.60, 10.30, 0.01)], [cuadro(-75.58, 10.30, 0.01)]] } },
  ],
}

describe('sectorEnCoordenada', () => {
  it('debeEncontrarElBarrioQueContieneElPunto', () => {
    expect(sectorEnCoordenada(GEOMETRIA, { latitud: 10.402, longitud: -75.538 })).toBe('manga')
  })

  it('debeLeerLaGeometriaEnOrdenLongitudLatitud', () => {
    expect(sectorEnCoordenada(GEOMETRIA, { latitud: -75.538, longitud: 10.402 })).toBeNull()
  })

  it('noDebeUbicarEnElBarrioUnPuntoQueCaeEnSuHueco', () => {
    expect(sectorEnCoordenada(GEOMETRIA, { latitud: 10.41, longitud: -75.53 })).toBeNull()
  })

  it('debeRecorrerCadaPoligonoDeUnMultiPolygon', () => {
    expect(sectorEnCoordenada(GEOMETRIA, { latitud: 10.305, longitud: -75.575 })).toBe('islas')
    expect(sectorEnCoordenada(GEOMETRIA, { latitud: 10.305, longitud: -75.585 })).toBeNull()
  })

  it('debeDevolverNuloFueraDeTodosLosBarrios', () => {
    expect(sectorEnCoordenada(GEOMETRIA, { latitud: 4.6, longitud: -74.08 })).toBeNull()
  })
})
