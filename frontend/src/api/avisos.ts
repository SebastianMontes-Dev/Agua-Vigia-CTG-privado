import { api, normalizarError } from './cliente'

export const MENSAJE_NEUTRO = 'Si la dirección es válida, te enviamos un correo. El enlace vence en 48 horas'

export type ResultadoAlta =
  | { tipo: 'recibido' }
  | { tipo: 'invalido' }
  | { tipo: 'esperar'; segundos: number | null }
  | { tipo: 'sin-red' }
  | { tipo: 'fallo' }

export type ResultadoEnlace =
  | { tipo: 'completado' }
  | { tipo: 'invalido' }
  | { tipo: 'esperar'; segundos: number | null }
  | { tipo: 'sin-red' }
  | { tipo: 'fallo' }

export function interpretarAlta(respuesta: Response | null): ResultadoAlta {
  if (respuesta === null) return { tipo: 'sin-red' }
  if (respuesta.status === 201) return { tipo: 'recibido' }
  if (respuesta.status === 400) return { tipo: 'invalido' }
  if (respuesta.status === 429) return { tipo: 'esperar', segundos: normalizarError(respuesta, null).segundosParaReintentar }
  return { tipo: 'fallo' }
}

export function interpretarEnlace(respuesta: Response | null): ResultadoEnlace {
  if (respuesta === null) return { tipo: 'sin-red' }
  if (respuesta.ok) return { tipo: 'completado' }
  if (respuesta.status === 400 || respuesta.status === 409) return { tipo: 'invalido' }
  if (respuesta.status === 429) return { tipo: 'esperar', segundos: normalizarError(respuesta, null).segundosParaReintentar }
  return { tipo: 'fallo' }
}

export async function pedirAvisos(correo: string, sectorIds: string[]): Promise<ResultadoAlta> {
  try {
    const { response } = await api.POST('/api/suscripciones', { body: { correo, sectorIds } })
    return interpretarAlta(response)
  } catch {
    return interpretarAlta(null)
  }
}

export async function actuarEnlace(accion: 'confirmar' | 'baja', token: string): Promise<ResultadoEnlace> {
  try {
    const ruta = accion === 'confirmar' ? '/api/suscripciones/confirmar' : '/api/suscripciones/cancelar'
    const respuesta = await fetch(`${ruta}?token=${encodeURIComponent(token)}`, {
      method: 'POST', headers: { Accept: 'application/json' },
    })
    return interpretarEnlace(respuesta)
  } catch {
    return interpretarEnlace(null)
  }
}
