import type { components } from './generado/esquema'
import { api, normalizarError, type ErrorApi } from './cliente'
import { sesion, type Alcance } from './sesion'

export type SesionVeedor = components['schemas']['SesionVeedor']
export type Cuenta = components['schemas']['UsuarioRespuesta']

/** Un permiso de `docs/api/panel-veedor.md`; el servidor valida siempre, la interfaz solo decide qué mostrar. */
export type Permiso =
  | 'VER_PANEL'
  | 'MODERAR_REPORTES'
  | 'GESTIONAR_CORTES'
  | 'REVISAR_INGESTA'
  | 'GESTIONAR_USUARIOS'
  | 'VER_AUDITORIA'
  | 'CONFIGURAR_SEGUNDO_FACTOR'

export type Resultado<T> = { ok: true; datos: T } | { ok: false; error: ErrorApi }

interface RespuestaCruda<T> {
  data?: T
  error?: unknown
  response: Response | null
}

/** Convierte cualquier llamada del cliente en un resultado sin excepciones: la pantalla decide con `error.tipo`. */
export async function intentar<T>(llamada: () => Promise<RespuestaCruda<T>>): Promise<Resultado<T>> {
  let cruda: RespuestaCruda<T>
  try {
    cruda = await llamada()
  } catch {
    return { ok: false, error: normalizarError(null, null) }
  }
  if (!cruda.response || !cruda.response.ok) return { ok: false, error: normalizarError(cruda.response, cruda.error) }
  return { ok: true, datos: cruda.data as T }
}

/** Mensaje de cada estado con el que el servidor rechaza un inicio de sesión (`cuenta-no-habilitada`). */
export function mensajeDeEstadoDeCuenta(estado: unknown): string {
  switch (estado) {
    case 'PENDIENTE_VERIFICACION': return 'Falta verificar tu correo. Abre el enlace que te enviamos.'
    case 'PENDIENTE_APROBACION': return 'Tu solicitud espera la aprobación de un administrador.'
    case 'INVITADA': return 'Falta aceptar tu invitación desde el enlace del correo.'
    case 'SUSPENDIDA': return 'Tu cuenta está suspendida. Habla con un administrador.'
    case 'RECHAZADA': return 'Tu solicitud de acceso fue rechazada.'
    default: return 'Esta cuenta todavía no puede ingresar al panel.'
  }
}

export type ResultadoIngreso =
  | { tipo: 'entro'; alcance: Alcance }
  | { tipo: 'pide-codigo' }
  | { tipo: 'credencial-invalida' }
  | { tipo: 'cuenta-no-habilitada'; mensaje: string }
  | { tipo: 'bloqueada'; segundos: number | null }
  | { tipo: 'esperar'; segundos: number | null }
  | { tipo: 'sin-red' }
  | { tipo: 'fallo' }

export function interpretarIngreso(error: ErrorApi): ResultadoIngreso {
  if (error.estado === null) return { tipo: 'sin-red' }
  if (error.tipo === 'segundo-factor-requerido') return { tipo: 'pide-codigo' }
  if (error.tipo === 'credencial-invalida') return { tipo: 'credencial-invalida' }
  if (error.tipo === 'cuenta-no-habilitada') {
    return { tipo: 'cuenta-no-habilitada', mensaje: mensajeDeEstadoDeCuenta(error.problema?.estado) }
  }
  if (error.tipo === 'cuenta-bloqueada') {
    const segundos = Number(error.problema?.segundosRestantes)
    return { tipo: 'bloqueada', segundos: Number.isFinite(segundos) ? segundos : null }
  }
  if (error.estado === 429) return { tipo: 'esperar', segundos: error.segundosParaReintentar }
  return { tipo: 'fallo' }
}

export async function iniciarSesion(correo: string, clave: string, codigoTotp?: string): Promise<ResultadoIngreso> {
  const resultado = await intentar(() => api.POST('/api/veedor/sesion', {
    body: { correo, clave, ...(codigoTotp ? { codigoTotp } : {}) },
  }))
  if (!resultado.ok) return interpretarIngreso(resultado.error)
  const { token, alcance } = resultado.datos
  if (!token) return { tipo: 'fallo' }
  const alcanceGuardado: Alcance = alcance === 'ALTA_SEGUNDO_FACTOR' ? 'ALTA_SEGUNDO_FACTOR' : 'COMPLETO'
  sesion.guardar(token, alcanceGuardado)
  return { tipo: 'entro', alcance: alcanceGuardado }
}

export function pedirCuenta(signal?: AbortSignal): Promise<Resultado<Cuenta>> {
  return intentar(() => api.GET('/api/veedor/yo', { signal }))
}

/** Cierra en el servidor **todas** las sesiones de la cuenta; aunque falle, esta se descarta en el cliente. */
export async function cerrarSesion(): Promise<void> {
  try {
    await api.POST('/api/veedor/sesion/cierre')
  } finally {
    sesion.limpiar()
  }
}

export function pedirAltaSegundoFactor(codigoActual?: string) {
  return intentar(() => api.POST('/api/veedor/segundo-factor/alta', {
    body: codigoActual ? { codigo: codigoActual } : undefined,
  }))
}

/** La confirmación devuelve una sesión completa nueva: se guarda para que la persona no vuelva a escribir su clave. */
export async function confirmarSegundoFactor(codigo: string): Promise<Resultado<SesionVeedor>> {
  const resultado = await intentar(() => api.POST('/api/veedor/segundo-factor/confirmacion', { body: { codigo } }))
  if (resultado.ok && resultado.datos.token) sesion.guardar(resultado.datos.token, 'COMPLETO')
  return resultado
}

export function desactivarSegundoFactor(codigo: string) {
  return intentar(() => api.POST('/api/veedor/segundo-factor/baja', { body: { codigo } }))
}

/** Cierra todas las sesiones, la actual incluida: tras el 204 hay que volver a ingresar. */
export async function cambiarClave(claveActual: string, claveNueva: string) {
  const resultado = await intentar(() => api.POST('/api/veedor/cuenta/clave', { body: { claveActual, claveNueva } }))
  if (resultado.ok) sesion.limpiar()
  return resultado
}

/** La política del servidor (`docs/api/cuentas-y-sesion.md`): 12–128 caracteres y no más de 72 bytes en UTF-8. */
export function validarClaveNueva(clave: string): string | null {
  if (clave.length < 12) return 'La clave necesita al menos 12 caracteres.'
  if (clave.length > 128) return 'La clave admite como máximo 128 caracteres.'
  if (new TextEncoder().encode(clave).length > 72) return 'La clave no puede pasar de 72 bytes: acorta los caracteres especiales.'
  if (new Set(clave).size < 5) return 'La clave repite demasiado los mismos caracteres.'
  if (clave.trim() !== clave) return 'La clave no puede empezar ni terminar con espacios.'
  return null
}
