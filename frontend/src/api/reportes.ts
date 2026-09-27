import type { components } from './generado/esquema'
import type { Coordenada } from '../dominio/ubicacion'
import { api, normalizarError } from './cliente'
import { obtenerHuella } from './huella'

export type TipoReporte = 'SIN_AGUA' | 'PRESION_BAJA' | 'SERVICIO_RESTABLECIDO'
export type Reporte = components['schemas']['ReporteRespuesta']

export type ResultadoEnvio =
  | { tipo: 'recibido'; reporte: Reporte }
  | { tipo: 'sin-red' }
  | { tipo: 'incierto' }
  | { tipo: 'cupo-agotado' }
  | { tipo: 'esperar'; segundos: number | null }
  | { tipo: 'rechazado'; mensaje: string }
  | { tipo: 'fallo' }

export type ResultadoConfirmacion =
  | { tipo: 'confirmado'; reporte: Reporte }
  | { tipo: 'no-disponible' }
  | { tipo: 'sin-red' }
  | { tipo: 'incierto' }
  | { tipo: 'esperar'; segundos: number | null }
  | { tipo: 'rechazado'; mensaje: string }
  | { tipo: 'fallo' }

export type ResultadoFoto =
  | { tipo: 'recibida' }
  | { tipo: 'muy-grande' }
  | { tipo: 'no-valida'; mensaje: string }
  | { tipo: 'fallo' }

/** Guía §6.2: cada respuesta tiene su mensaje; se decide por `type`, nunca por el texto del error. */
export function interpretarEnvio(respuesta: Response | null, cuerpo: unknown): ResultadoEnvio {
  // Sin respuesta, la petición pudo haber llegado: no se presenta como recibido ni como no enviado.
  if (respuesta === null) return { tipo: 'incierto' }
  if (respuesta.status === 201) return { tipo: 'recibido', reporte: cuerpo as Reporte }
  const error = normalizarError(respuesta, cuerpo)
  if (error.tipo === 'limite-reportes-excedido') return { tipo: 'cupo-agotado' }
  if (respuesta.status === 429) return { tipo: 'esperar', segundos: error.segundosParaReintentar }
  if (respuesta.status === 400) return { tipo: 'rechazado', mensaje: error.mensaje }
  return { tipo: 'fallo' }
}

/** Nunca se reintenta solo (plan §6.4): un POST repetido contaría dos veces al mismo vecino. */
export async function enviarReporte(
  sectorId: string,
  tipo: TipoReporte,
  coordenada: Coordenada | null = null,
  enLinea: () => boolean = () => navigator.onLine,
): Promise<ResultadoEnvio> {
  if (!enLinea()) return { tipo: 'sin-red' }
  const huella = await obtenerHuella()
  const cuerpo = coordenada ? { tipo, huella, sectorId, coordenada } : { tipo, huella, sectorId }
  try {
    const { data, error, response } = await api.POST('/api/reportes', { body: cuerpo })
    return interpretarEnvio(response, data ?? error)
  } catch {
    return interpretarEnvio(null, null)
  }
}

/** Un `404` es igual si el reporte no existe o si moderación lo descartó: para quien confirma, ya no cuenta. */
export function interpretarConfirmacion(respuesta: Response | null, cuerpo: unknown): ResultadoConfirmacion {
  if (respuesta === null) return { tipo: 'incierto' }
  if (respuesta.status === 200) return { tipo: 'confirmado', reporte: cuerpo as Reporte }
  if (respuesta.status === 404) return { tipo: 'no-disponible' }
  const error = normalizarError(respuesta, cuerpo)
  if (respuesta.status === 429) return { tipo: 'esperar', segundos: error.segundosParaReintentar }
  if (respuesta.status === 400) return { tipo: 'rechazado', mensaje: error.mensaje }
  return { tipo: 'fallo' }
}

/** Se envía solo cuando la persona lo pide: abrir el enlace no confirma nada (guía §5.1). */
export async function confirmarReporte(
  reporteId: string,
  enLinea: () => boolean = () => navigator.onLine,
): Promise<ResultadoConfirmacion> {
  if (!enLinea()) return { tipo: 'sin-red' }
  const huella = await obtenerHuella()
  try {
    const { data, error, response } = await api.POST('/api/reportes/{id}/confirmar', {
      params: { path: { id: reporteId } },
      body: { huella },
    })
    return interpretarConfirmacion(response, data ?? error)
  } catch {
    return interpretarConfirmacion(null, null)
  }
}

export function interpretarFoto(respuesta: Response | null, cuerpo: unknown): ResultadoFoto {
  if (respuesta?.ok) return { tipo: 'recibida' }
  if (respuesta?.status === 413) return { tipo: 'muy-grande' }
  if (respuesta?.status === 400) return { tipo: 'no-valida', mensaje: normalizarError(respuesta, cuerpo).mensaje }
  return { tipo: 'fallo' }
}

export const TIPOS_FOTO = ['image/jpeg', 'image/png', 'image/webp']
export const MAXIMO_FOTO_BYTES = 10 * 1024 * 1024

export async function enviarFoto(reporteId: string, foto: File): Promise<ResultadoFoto> {
  if (foto.size > MAXIMO_FOTO_BYTES) return { tipo: 'muy-grande' }
  const formulario = new FormData()
  formulario.append('foto', foto)
  try {
    // openapi-fetch serializaría el cuerpo como JSON: el multipart va con fetch y el navegador pone el boundary.
    const respuesta = await fetch(`/api/reportes/${encodeURIComponent(reporteId)}/foto`, { method: 'POST', body: formulario })
    return interpretarFoto(respuesta, await respuesta.json().catch(() => null))
  } catch {
    return { tipo: 'fallo' }
  }
}
