import type { FeatureCollection, Geometry } from 'geojson'
import type { components } from './generado/esquema'
import { normalizarError, type ErrorApi } from './cliente'
export class FalloCiudadano extends Error {
  constructor(public dato: ErrorApi) { super(dato.mensaje) }
}
export async function pedir<T>(ruta: string, opciones: RequestInit = {}): Promise<{ dato: T; respuesta: Response }> {
  let respuesta: Response
  try { respuesta = await fetch(ruta, opciones) } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new FalloCiudadano(normalizarError(null, null))
  }
  const dato: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) throw new FalloCiudadano(normalizarError(respuesta, dato))
  return { dato: dato as T, respuesta }
}
export function mensajeFallo(error: unknown, envio = false, foto = false): string {
  if (!(error instanceof FalloCiudadano)) return 'No pudimos completar la acción. Inténtalo a mano otra vez.'
  switch (error.dato.tipo) {
    case 'limite-reportes-excedido': return 'Ya recibimos tres reportes tuyos en este barrio. Espera 30 minutos antes de reportar otra vez.'
    case 'limite-de-peticiones-excedido': return 'Hay muchas peticiones en este momento. Espera un poco antes de intentarlo a mano otra vez.'
    case 'peticion-invalida': return foto ? 'La foto no es un JPEG, PNG o WebP válido. Tu reporte sigue guardado; elige otra foto.' : 'No pudimos ubicar o validar el reporte. Elige el barrio a mano; la ubicación puede quedar fuera de Cartagena.'
    case 'recurso-no-encontrado': return 'Este reporte o barrio no está disponible. El reporte puede haber sido descartado.'
    case 'archivo-demasiado-grande': return 'La foto debe pesar menos de 10 MB. Tu reporte sigue guardado.'
    default: return envio ? 'No pudimos comprobar si llegó. Revisa tu conexión antes de intentarlo a mano; no se reenviará solo.' : 'No pudimos consultar los datos. Revisa tu conexión e inténtalo a mano.'
  }
}
type Geometria = FeatureCollection<Geometry>
function abrirGeometria(): Promise<IDBDatabase> {
  return new Promise((resolver, rechazar) => {
    const solicitud = indexedDB.open('aguavigia-geometria', 1)
    solicitud.onupgradeneeded = () => solicitud.result.createObjectStore('geometria')
    solicitud.onsuccess = () => resolver(solicitud.result)
    solicitud.onerror = () => rechazar(solicitud.error)
  })
}
let geometriaPendiente: Promise<Geometria> | null = null
export function cargarGeometria(): Promise<Geometria> {
  return geometriaPendiente ??= (async () => {
    let base: IDBDatabase | null = null
    try {
      base = await abrirGeometria()
      const guardada = await new Promise<Geometria | undefined>((resolver, rechazar) => {
        const lectura = base!.transaction('geometria').objectStore('geometria').get('v1')
        lectura.onsuccess = () => resolver(lectura.result as Geometria | undefined)
        lectura.onerror = () => rechazar(lectura.error)
      })
      if (guardada) { base.close(); return guardada }
    } catch { base?.close(); base = null }
    if (document.hidden) throw new Error('La geometría se consulta al volver a la pestaña')
    const { dato } = await pedir<Geometria>('/api/sectores/geometria')
    if (base) {
      try { base.transaction('geometria', 'readwrite').objectStore('geometria').put(dato, 'v1') } finally { base.close() }
    }
    return dato
  })().catch((error: unknown) => { geometriaPendiente = null; throw error })
}
export type Corte = components['schemas']['CorteRespuesta']
export type Reporte = components['schemas']['ReporteRespuesta']
export const enviarReporte = (solicitud: components['schemas']['SolicitudReporte']) => pedir<Reporte>('/api/reportes', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(solicitud) })
