import type { components } from '../api/generado/esquema'
import { ORDEN_ESTADOS, type EstadoServicio } from './estados'

export type Sector = components['schemas']['SectorRespuesta']
export type ClaveConteo = EstadoServicio | 'SIN_DATOS'

const MINUSCULAS = new Set(['de', 'del', 'la', 'las', 'los', 'el', 'y', 'e'])
const ROMANOS = /^(?=[ivx])x{0,3}(ix|iv|v?i{0,3})$/i

/** El GeoJSON oficial trae los nombres en mayúsculas; se presentan en tipo oración sin cambiar su ortografía. */
export function nombreLegible(nombre: string | undefined): string {
  if (!nombre) return 'Barrio sin nombre'
  return nombre.trim().toLowerCase().split(/\s+/).map((palabra, i) => {
    if (ROMANOS.test(palabra)) return palabra.toUpperCase()
    if (i > 0 && MINUSCULAS.has(palabra)) return palabra
    return palabra.replace(/^\p{L}/u, (letra) => letra.toUpperCase())
  }).join(' ')
}

export function normalizarBusqueda(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9ñ]+/g, ' ').trim()
}

/** Primero los que empiezan por el término, después los que lo contienen en otra palabra; el resto se descarta. */
export function buscarSectores(sectores: readonly Sector[], termino: string, limite = 8): Sector[] {
  const buscado = normalizarBusqueda(termino)
  if (!buscado) return []
  const alInicio: Sector[] = []
  const dentro: Sector[] = []
  for (const sector of sectores) {
    const nombre = normalizarBusqueda(sector.nombre ?? '')
    if (nombre.startsWith(buscado)) alInicio.push(sector)
    else if (nombre.includes(` ${buscado}`) || nombre.includes(buscado)) dentro.push(sector)
  }
  return [...alInicio, ...dentro].slice(0, limite)
}

export function claveConteo(sector: Sector): ClaveConteo {
  return sector.estado ?? 'SIN_DATOS'
}

export const ORDEN_CONTEO: readonly ClaveConteo[] = [...ORDEN_ESTADOS, 'SIN_DATOS']

export function contarPorEstado(sectores: readonly Sector[]): Record<ClaveConteo, number> {
  const conteo = Object.fromEntries(ORDEN_CONTEO.map((clave) => [clave, 0])) as Record<ClaveConteo, number>
  for (const sector of sectores) conteo[claveConteo(sector)] += 1
  return conteo
}

/** Barrios con algo distinto de servicio normal, del más grave al menos grave y, dentro de cada estado, del cambio más reciente. */
export function barriosConNovedades(sectores: readonly Sector[]): Sector[] {
  const severidad = (sector: Sector) => (sector.estado ? ORDEN_ESTADOS.indexOf(sector.estado) : ORDEN_ESTADOS.length)
  return sectores
    .filter((sector) => sector.estado && sector.estado !== 'CON_SERVICIO')
    .sort((a, b) => severidad(a) - severidad(b)
      || new Date(b.actualizadoEn ?? 0).getTime() - new Date(a.actualizadoEn ?? 0).getTime())
}
