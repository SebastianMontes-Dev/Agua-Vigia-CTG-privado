import type { components } from './generado/esquema'
import { api, normalizarError } from './cliente'

type Cliente = typeof api
import { sesion, type Alcance } from './sesion'

export type SesionVeedor = components['schemas']['SesionVeedor']
export type Cuenta = components['schemas']['UsuarioRespuesta']

export type ResultadoIngreso =
  | { tipo: 'ingresado'; alcance: Alcance }
  | { tipo: 'pide-codigo' }
  | { tipo: 'credencial-invalida' }
  | { tipo: 'no-habilitada'; estado: string | null; mensaje: string }
  | { tipo: 'bloqueada'; segundos: number | null }
  | { tipo: 'esperar'; segundos: number | null }
  | { tipo: 'rechazado'; mensaje: string }
  | { tipo: 'sin-red' }
  | { tipo: 'fallo' }

export type ResultadoAlta =
  | { tipo: 'alta'; uri: string; secreto: string }
  | { tipo: 'ya-activo' }
  | { tipo: 'codigo-incorrecto' }
  | { tipo: 'sesion-terminada' }
  | { tipo: 'esperar'; segundos: number | null }
  | { tipo: 'sin-red' }
  | { tipo: 'fallo' }

export type ResultadoConfirmacionFactor =
  | { tipo: 'activado' }
  | { tipo: 'codigo-incorrecto' }
  | { tipo: 'sin-alta' }
  | { tipo: 'sesion-terminada' }
  | { tipo: 'esperar'; segundos: number | null }
  | { tipo: 'sin-red' }
  | { tipo: 'fallo' }

function numero(valor: unknown): number | null {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null
}

function alcanceDe(valor: unknown): Alcance {
  return valor === 'ALTA_SEGUNDO_FACTOR' ? 'ALTA_SEGUNDO_FACTOR' : 'COMPLETO'
}

/** cuentas-y-sesion.md: un 401 de segundo factor no es clave mala, y la clave mala no dice qué campo falló. */
export function interpretarIngreso(respuesta: Response | null, cuerpo: unknown): ResultadoIngreso | { tipo: 'sesion'; sesion: SesionVeedor } {
  if (respuesta === null) return { tipo: 'sin-red' }
  if (respuesta.status === 200) return { tipo: 'sesion', sesion: cuerpo as SesionVeedor }
  const error = normalizarError(respuesta, cuerpo)
  if (error.tipo === 'segundo-factor-requerido') return { tipo: 'pide-codigo' }
  if (respuesta.status === 401) return { tipo: 'credencial-invalida' }
  if (error.tipo === 'cuenta-no-habilitada') {
    const estado = error.problema?.estado
    return { tipo: 'no-habilitada', estado: typeof estado === 'string' ? estado : null, mensaje: error.mensaje }
  }
  if (respuesta.status === 423) return { tipo: 'bloqueada', segundos: numero(error.problema?.segundosRestantes) }
  if (respuesta.status === 429) return { tipo: 'esperar', segundos: error.segundosParaReintentar }
  if (respuesta.status === 400) return { tipo: 'rechazado', mensaje: error.mensaje }
  return { tipo: 'fallo' }
}

/** Nunca se reintenta solo: cada intento fallido cuenta para el bloqueo de la cuenta. */
export async function iniciarSesion(correo: string, clave: string, codigoTotp?: string, cliente: Cliente = api): Promise<ResultadoIngreso> {
  let resultado: ReturnType<typeof interpretarIngreso>
  try {
    const { data, error, response } = await cliente.POST('/api/veedor/sesion', {
      body: codigoTotp ? { correo, clave, codigoTotp } : { correo, clave },
    })
    resultado = interpretarIngreso(response, data ?? error)
  } catch {
    resultado = interpretarIngreso(null, null)
  }
  if (resultado.tipo !== 'sesion') return resultado
  if (!resultado.sesion.token) return { tipo: 'fallo' }
  const alcance = alcanceDe(resultado.sesion.alcance)
  sesion.guardar(resultado.sesion.token, alcance)
  return { tipo: 'ingresado', alcance }
}

function errorDeFactor(respuesta: Response, cuerpo: unknown) {
  const error = normalizarError(respuesta, cuerpo)
  if (respuesta.status === 401) {
    return error.tipo === 'credencial-invalida' ? { tipo: 'codigo-incorrecto' as const } : { tipo: 'sesion-terminada' as const }
  }
  if (respuesta.status === 429) return { tipo: 'esperar' as const, segundos: error.segundosParaReintentar }
  return null
}

export function interpretarAlta(respuesta: Response | null, cuerpo: unknown): ResultadoAlta {
  if (respuesta === null) return { tipo: 'sin-red' }
  if (respuesta.status === 200) {
    const alta = cuerpo as components['schemas']['AltaSegundoFactorRespuesta']
    return alta.uri && alta.secreto ? { tipo: 'alta', uri: alta.uri, secreto: alta.secreto } : { tipo: 'fallo' }
  }
  if (respuesta.status === 409) return { tipo: 'ya-activo' }
  return errorDeFactor(respuesta, cuerpo) ?? { tipo: 'fallo' }
}

/** Genera un secreto nuevo que reemplaza al pendiente: se pide solo al tocar, nunca al montar la pantalla. */
export async function iniciarAltaSegundoFactor(cliente: Cliente = api): Promise<ResultadoAlta> {
  try {
    const { data, error, response } = await cliente.POST('/api/veedor/segundo-factor/alta', {})
    return interpretarAlta(response, data ?? error)
  } catch {
    return interpretarAlta(null, null)
  }
}

export function interpretarConfirmacionFactor(respuesta: Response | null, cuerpo: unknown): ResultadoConfirmacionFactor | { tipo: 'sesion'; sesion: SesionVeedor } {
  if (respuesta === null) return { tipo: 'sin-red' }
  if (respuesta.status === 200) return { tipo: 'sesion', sesion: cuerpo as SesionVeedor }
  if (respuesta.status === 409) return { tipo: 'sin-alta' }
  return errorDeFactor(respuesta, cuerpo) ?? { tipo: 'fallo' }
}

/** La respuesta trae una sesión completa que reemplaza a la restringida: no se vuelve a pedir la clave. */
export async function confirmarSegundoFactor(codigo: string, cliente: Cliente = api): Promise<ResultadoConfirmacionFactor> {
  let resultado: ReturnType<typeof interpretarConfirmacionFactor>
  try {
    const { data, error, response } = await cliente.POST('/api/veedor/segundo-factor/confirmacion', { body: { codigo } })
    resultado = interpretarConfirmacionFactor(response, data ?? error)
  } catch {
    resultado = interpretarConfirmacionFactor(null, null)
  }
  if (resultado.tipo !== 'sesion') return resultado
  if (!resultado.sesion.token) return { tipo: 'fallo' }
  sesion.guardar(resultado.sesion.token, alcanceDe(resultado.sesion.alcance))
  return { tipo: 'activado' }
}

/** El servidor revoca todas las sesiones de la cuenta; aunque falle la petición, este navegador olvida el token. */
export async function cerrarSesion(cliente: Cliente = api): Promise<void> {
  try {
    await cliente.POST('/api/veedor/sesion/cierre')
  } catch {
    // Sin red la sesión sigue viva en el servidor hasta que caduque; aquí se borra igual.
  } finally {
    sesion.limpiar()
  }
}

export async function consultarCuentaActual({ signal }: { signal?: AbortSignal } = {}): Promise<Cuenta> {
  const { data, error, response } = await api.GET('/api/veedor/yo', { signal })
    .catch(() => ({ data: undefined, error: undefined, response: null }))
  if (!response || !response.ok || !data) throw normalizarError(response ?? null, error)
  return data
}
