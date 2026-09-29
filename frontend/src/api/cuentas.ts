import { api, type ErrorApi } from './cliente'
import { intentar, type Resultado } from './panel'

export function solicitarCuenta(nombre: string, correo: string, clave: string) {
  return intentar(() => api.POST('/api/cuentas/registro', { body: { nombre, correo, clave } }))
}

export function verificarCorreo(token: string) {
  return intentar(() => api.POST('/api/cuentas/verificacion', { params: { query: { token } } }))
}

export function reenviarVerificacion(correo: string) {
  return intentar(() => api.POST('/api/cuentas/verificacion/reenvio', { body: { correo } }))
}

export function aceptarInvitacion(token: string, clave: string) {
  return intentar(() => api.POST('/api/cuentas/invitacion', { body: { token, clave } }))
}

export function pedirRestablecimiento(correo: string) {
  return intentar(() => api.POST('/api/cuentas/restablecimiento', { body: { correo } }))
}

export function fijarClaveNueva(token: string, clave: string) {
  return intentar(() => api.POST('/api/cuentas/clave', { body: { token, clave } }))
}

export type Veredicto =
  | { tipo: 'listo' }
  | { tipo: 'invalido'; detalle: string | null }
  | { tipo: 'esperar'; segundos: number | null }
  | { tipo: 'sin-red' }
  | { tipo: 'fallo' }

/** Un 4xx que no es 429 significa «esto no se puede completar»; el detalle del servidor solo se muestra en el 400. */
export function veredictoDe(resultado: Resultado<unknown>): Veredicto {
  if (resultado.ok) return { tipo: 'listo' }
  const error: ErrorApi = resultado.error
  if (error.estado === null) return { tipo: 'sin-red' }
  if (error.estado === 429) return { tipo: 'esperar', segundos: error.segundosParaReintentar }
  if (error.estado >= 400 && error.estado < 500) {
    return { tipo: 'invalido', detalle: error.estado === 400 && error.problema ? error.mensaje : null }
  }
  return { tipo: 'fallo' }
}
