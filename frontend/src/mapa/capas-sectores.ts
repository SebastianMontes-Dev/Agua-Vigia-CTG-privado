import type { ExpressionSpecification, LayerSpecification } from 'maplibre-gl'
import type { FeatureCollection, MultiPolygon, Polygon } from 'geojson'
import type { GeometriaSectores } from '../api/geometria'
import type { Sector } from '../dominio/sectores'
import { nombreLegible } from '../dominio/sectores'

export const FUENTE_SECTORES = 'sectores'
export const IMAGEN_TRAMA = 'trama-sin-datos'
export const CAPA_RELLENO = 'sectores-relleno'
export const CAPA_TRAMA = 'sectores-sin-datos'

export interface ColoresMapa {
  oscuro: boolean
  conServicio: string
  sinServicio: string
  presionBaja: string
  corteProgramado: string
  superficie: string
  tinta: string
  tinta2: string
  laton: string
}

export interface PropiedadesSector { id: string; nombre: string; estado: string }

/** Solo se dibujan los barrios cuyo estado se conoce ya: pintar antes del listado sería inventar un estado. */
export function unirSectores(
  geometria: GeometriaSectores,
  sectores: ReadonlyMap<string, Sector>,
): FeatureCollection<Polygon | MultiPolygon, PropiedadesSector> {
  const features = geometria.features.flatMap((feature) => {
    const id = String(feature.id ?? '')
    const sector = sectores.get(id)
    if (!sector) return []
    return [{
      type: 'Feature' as const,
      id,
      geometry: feature.geometry,
      properties: { id, nombre: nombreLegible(sector.nombre ?? feature.properties?.nombre), estado: sector.estado ?? 'SIN_DATOS' },
    }]
  })
  return { type: 'FeatureCollection', features }
}

export function leerColoresMapa(): ColoresMapa {
  const css = getComputedStyle(document.documentElement)
  const token = (nombre: string) => css.getPropertyValue(nombre).trim()
  return {
    oscuro: css.colorScheme === 'dark',
    conServicio: token('--estado-con-servicio'),
    sinServicio: token('--estado-sin-servicio'),
    presionBaja: token('--estado-presion-baja'),
    corteProgramado: token('--estado-corte-programado'),
    superficie: token('--superficie'),
    tinta: token('--tinta'),
    tinta2: token('--tinta-2'),
    laton: token('--laton'),
  }
}

function colorPorEstado(colores: ColoresMapa): ExpressionSpecification {
  return ['match', ['get', 'estado'],
    'SIN_SERVICIO', colores.sinServicio,
    'CORTE_PROGRAMADO', colores.corteProgramado,
    'PRESION_BAJA', colores.presionBaja,
    'CON_SERVICIO', colores.conServicio,
    colores.tinta2]
}

/**
 * identidad.md §4: relleno a baja opacidad (16 % claro, 22 % oscuro; más para sin servicio), borde de 0,8 px del
 * color del estado, trama para sin datos y contorno de latón en el elegido, con los demás atenuados.
 */
export function capasSectores(colores: ColoresMapa, seleccionado: string | null): LayerSpecification[] {
  const base = colores.oscuro ? 0.22 : 0.16
  const fuerte = colores.oscuro ? 0.34 : 0.28
  const elegido: ExpressionSpecification = ['==', ['get', 'id'], seleccionado ?? '']
  const atenuar = (valor: number): ExpressionSpecification | number =>
    seleccionado ? ['case', elegido, valor * 1.6, valor * 0.55] : valor
  const opacidad: ExpressionSpecification = ['match', ['get', 'estado'], 'SIN_SERVICIO', atenuar(fuerte), atenuar(base)]
  const sinDatos: ExpressionSpecification = ['==', ['get', 'estado'], 'SIN_DATOS']

  return [
    { id: CAPA_RELLENO, type: 'fill', source: FUENTE_SECTORES, filter: ['!', sinDatos],
      paint: { 'fill-color': colorPorEstado(colores), 'fill-opacity': opacidad } },
    { id: CAPA_TRAMA, type: 'fill', source: FUENTE_SECTORES, filter: sinDatos,
      paint: { 'fill-pattern': IMAGEN_TRAMA, 'fill-opacity': atenuar(colores.oscuro ? 0.5 : 0.75) } },
    { id: 'sectores-borde', type: 'line', source: FUENTE_SECTORES,
      paint: { 'line-color': colorPorEstado(colores), 'line-width': ['interpolate', ['linear'], ['zoom'], 11, 0.6, 15, 1.2],
        'line-opacity': seleccionado ? ['case', elegido, 1, 0.5] : 0.9 } },
    { id: 'sector-elegido-halo', type: 'line', source: FUENTE_SECTORES, filter: elegido,
      paint: { 'line-color': colores.superficie, 'line-width': 5.5 } },
    { id: 'sector-elegido', type: 'line', source: FUENTE_SECTORES, filter: elegido,
      layout: { 'line-join': 'round' }, paint: { 'line-color': colores.laton, 'line-width': 2.5 } },
    { id: 'sectores-nombres', type: 'symbol', source: FUENTE_SECTORES, minzoom: 13.5,
      layout: { 'text-field': ['get', 'nombre'], 'text-font': ['Noto Sans Medium'], 'text-size': 12,
        'text-max-width': 8, 'text-padding': 4 },
      paint: { 'text-color': colores.tinta, 'text-halo-color': colores.superficie, 'text-halo-width': 1.6 } },
  ]
}

/** Baldosa de 8 × 8 px de trama-sin-datos.svg (guía §2.4), dibujada con los tokens del tema en uso. */
export function dibujarTrama(colores: ColoresMapa, densidad = 2): ImageData | null {
  const lado = 8 * densidad
  const lienzo = document.createElement('canvas')
  lienzo.width = lienzo.height = lado
  const contexto = lienzo.getContext('2d')
  if (!contexto) return null
  contexto.fillStyle = colores.superficie
  contexto.fillRect(0, 0, lado, lado)
  contexto.strokeStyle = colores.tinta2
  contexto.lineWidth = densidad
  contexto.beginPath()
  for (const desfase of [-lado, 0, lado]) {
    contexto.moveTo(desfase, lado)
    contexto.lineTo(desfase + lado, 0)
  }
  contexto.stroke()
  return contexto.getImageData(0, 0, lado, lado)
}
