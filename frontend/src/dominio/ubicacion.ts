import type { Position } from 'geojson'
import type { GeometriaSectores } from '../api/geometria'

export interface Coordenada { latitud: number; longitud: number }

function dentroDelAnillo(anillo: readonly Position[], longitud: number, latitud: number): boolean {
  let dentro = false
  for (let i = 0, j = anillo.length - 1; i < anillo.length; j = i++) {
    const [xi = 0, yi = 0] = anillo[i] ?? []
    const [xj = 0, yj = 0] = anillo[j] ?? []
    if (yi > latitud !== yj > latitud && longitud < ((xj - xi) * (latitud - yi)) / (yj - yi) + xi) dentro = !dentro
  }
  return dentro
}

function dentroDelPoligono([exterior, ...huecos]: readonly Position[][], longitud: number, latitud: number): boolean {
  if (!exterior || !dentroDelAnillo(exterior, longitud, latitud)) return false
  return !huecos.some((hueco) => dentroDelAnillo(hueco, longitud, latitud))
}

/** ADR-076: el barrio se busca en la geometría que ya está en el dispositivo; la coordenada no sale para esto. */
export function sectorEnCoordenada(geometria: GeometriaSectores, { latitud, longitud }: Coordenada): string | null {
  for (const feature of geometria.features) {
    // GeoJSON admite `geometry: null`; un barrio sin dibujo no contiene ningún punto.
    const geometry = feature.geometry as typeof feature.geometry | null
    if (!geometry) continue
    const poligonos = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates
    if (feature.id != null && poligonos.some((poligono) => dentroDelPoligono(poligono, longitud, latitud))) return String(feature.id)
  }
  return null
}
