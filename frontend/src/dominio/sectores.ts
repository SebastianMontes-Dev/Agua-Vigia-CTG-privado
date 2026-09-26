import type { FeatureCollection, Geometry } from 'geojson'
import type { components } from '../api/generado/esquema'
export type Sector = components['schemas']['SectorRespuesta']
export function unirSectores(geometria: FeatureCollection<Geometry>, sectores: Sector[]): FeatureCollection<Geometry> {
  const porId = new Map(sectores.map((sector) => [sector.id, sector]))
  return { ...geometria, features: geometria.features.map((figura) => ({ ...figura,
    properties: { ...figura.properties, ...porId.get(String(figura.id)), id: String(figura.id), estado: porId.get(String(figura.id))?.estado ?? null },
  })) }
}
export function verificacionAntigua(sector: Sector, ahora = Date.now()) {
  return sector.estado != null && (!sector.verificadoEn || ahora - Date.parse(sector.verificadoEn) >= 86_400_000)
}
export function fechaCartagena(fecha: string | null | undefined): string {
  if (!fecha || Number.isNaN(Date.parse(fecha))) return 'Sin fecha registrada'
  return new Intl.DateTimeFormat('es-CO', { timeZone: 'America/Bogota', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', hour12: true }).format(new Date(fecha))
}
export function habitantes(poblacion: number | null | undefined) {
  return poblacion == null ? 'Sin dato censal' : `${poblacion.toLocaleString('es-CO')} habitantes`
}
