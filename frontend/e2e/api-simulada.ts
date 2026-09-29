import type { Page, Route } from '@playwright/test'
import { cumplimientoDeEjemplo, estadisticasDeEjemplo, serieDeEjemplo } from '../src/pruebas/datos/historia'

// Datos de prueba: tres barrios con coordenadas aproximadas de Cartagena. No describen su estado real.
function cuadro(lon: number, lat: number) {
  const d = 0.006
  return [[[lon, lat], [lon + d, lat], [lon + d, lat + d], [lon, lat + d], [lon, lat]]]
}

export const GEOMETRIA = {
  type: 'FeatureCollection',
  features: [
    { type: 'Feature', id: 'armenia', properties: { nombre: 'ARMENIA' }, geometry: { type: 'Polygon', coordinates: cuadro(-75.505, 10.405) } },
    { type: 'Feature', id: 'manga', properties: { nombre: 'MANGA' }, geometry: { type: 'Polygon', coordinates: cuadro(-75.535, 10.41) } },
    { type: 'Feature', id: 'el-bosque', properties: { nombre: 'EL BOSQUE' }, geometry: { type: 'Polygon', coordinates: cuadro(-75.52, 10.395) } },
  ],
}

export function listado(ahora = new Date()) {
  const haceHoras = (horas: number) => new Date(ahora.getTime() - horas * 3_600_000).toISOString()
  return {
    generadoEn: ahora.toISOString(),
    sectores: [
      { id: 'armenia', nombre: 'ARMENIA', poblacion: 1061, estado: 'SIN_SERVICIO', actualizadoEn: haceHoras(1), verificadoEn: haceHoras(1) },
      { id: 'el-bosque', nombre: 'EL BOSQUE', poblacion: 6754, estado: 'CON_SERVICIO', actualizadoEn: haceHoras(52), verificadoEn: haceHoras(30) },
      { id: 'manga', nombre: 'MANGA', poblacion: null, estado: null, actualizadoEn: null, verificadoEn: null },
    ],
  }
}

export function cortes(ahora = new Date()) {
  const desplazar = (horas: number) => new Date(ahora.getTime() + horas * 3_600_000).toISOString()
  return {
    armenia: [
      { id: 'c1', sectoresAfectados: ['armenia'], inicio: desplazar(-3), finPrometido: desplazar(4), finReal: null, causa: 'Reparación de tubería', origen: 'OFICIAL_ACUACAR', estado: 'CONFIRMADO' },
      { id: 'c0', sectoresAfectados: ['armenia'], inicio: desplazar(-200), finPrometido: desplazar(-198), finReal: desplazar(-192), causa: 'Mantenimiento', origen: 'VEEDOR', estado: 'RESTABLECIDO' },
    ],
  } as Record<string, unknown[]>
}

const TIPO = 'https://aguavigia.example/errores/'

export function problema(estado: number, tipo: string, detalle: string) {
  return { status: estado, contentType: 'application/problem+json', body: JSON.stringify({ type: `${TIPO}${tipo}`, title: tipo, status: estado, detail: detalle }) }
}

interface Opciones {
  sectores?: (route: Route) => Promise<void>
  estadisticas?: (route: Route) => Promise<void>
  reporte?: (route: Route) => Promise<void>
  confirmacion?: (route: Route) => Promise<void>
  cumplimiento?: (route: Route) => Promise<void>
  serie?: (route: Route) => Promise<void>
}

/** Simula la API pública para las E2E que corren sin backend (CI). Devuelve los cuerpos de los reportes y confirmaciones. */
export async function simularApi(page: Page, opciones: Opciones = {}) {
  const reportes: unknown[] = []
  const confirmaciones: { id: string; cuerpo: unknown }[] = []
  await page.route('**/api/sectores/stream', (route) =>
    route.fulfill({ status: 200, contentType: 'text/event-stream', body: 'retry:60000\nevent:sectores\ndata:{}\n\n' }))
  await page.route('**/api/sectores/geometria', (route) =>
    route.fulfill({ status: 200, contentType: 'application/geo+json', body: JSON.stringify(GEOMETRIA) }))
  await page.route(/\/api\/cumplimiento\/serie\.csv(?:\?|$)/, (route) => route.fulfill({
    status: 200, contentType: 'text/csv', body: 'periodo;porcentaje_cumplimiento\n2026-04;100',
  }))
  await page.route(/\/api\/cumplimiento\/serie(?:\?|$)/, opciones.serie ?? ((route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(serieDeEjemplo),
  })))
  await page.route(/\/api\/cumplimiento(?:\/sectores\/[^/?]+)?(?:\?|$)/, opciones.cumplimiento ?? ((route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(cumplimientoDeEjemplo),
  })))
  await page.route(/\/api\/sectores\/[^/]+\/cortes/, (route) => {
    const id = new URL(route.request().url()).pathname.split('/')[3] ?? ''
    const lista = cortes()[id] ?? []
    return route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify(lista),
      headers: { 'X-Total-Count': String(lista.length), 'X-Total-Pages': '1', 'X-Page': '0', 'X-Page-Size': '10' },
    })
  })
  await page.route('**/api/sectores', opciones.sectores ?? ((route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(listado()) })))
  await page.route('**/api/estadisticas', opciones.estadisticas ?? ((route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(estadisticasDeEjemplo) })))
  await page.route('**/api/reportes', async (route) => {
    reportes.push(route.request().postDataJSON())
    if (opciones.reporte) return opciones.reporte(route)
    return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 'r1', sectorId: 'armenia', tipo: 'SIN_AGUA', confirmaciones: 0 }) })
  })
  await page.route(/\/api\/sectores\/(?!stream$|geometria$)[^/]+$/, (route) => {
    const id = new URL(route.request().url()).pathname.split('/')[3] ?? ''
    const sector = listado().sectores.find((s) => s.id === id)
    return sector
      ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(sector) })
      : route.fulfill(problema(404, 'recurso-no-encontrado', `No existe el sector '${id}'`))
  })
  await page.route(/\/api\/reportes\/[^/]+\/confirmar$/, async (route) => {
    const id = new URL(route.request().url()).pathname.split('/')[3] ?? ''
    confirmaciones.push({ id, cuerpo: route.request().postDataJSON() })
    if (opciones.confirmacion) return opciones.confirmacion(route)
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id, sectorId: 'armenia', tipo: 'SIN_AGUA', confirmaciones: 2 }) })
  })
  // El extracto PMTiles viaja por Git LFS y en CI puede faltar: el mapa base no es parte de estas pruebas.
  await page.route('**/mapa/cartagena.pmtiles', (route) => route.abort())
  return { reportes, confirmaciones }
}
