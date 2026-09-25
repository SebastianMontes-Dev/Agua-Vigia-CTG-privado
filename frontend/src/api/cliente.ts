import createClient, { type Middleware } from 'openapi-fetch'
import type { components, paths } from './generado/esquema'
import { sesion } from './sesion'

export type Problema = components['schemas']['ProblemDetail'] & Record<string, unknown>

const PREFIJO_TIPO = 'https://aguavigia.example/errores/'

/** Slugs de `docs/api/errores-y-limites.md`. Se reacciona por estos, nunca por `title` ni `detail`. */
export type TipoProblema =
  | 'peticion-invalida'
  | 'credencial-invalida'
  | 'segundo-factor-requerido'
  | 'sesion-sin-cuenta'
  | 'acceso-denegado'
  | 'cuenta-no-habilitada'
  | 'recurso-no-encontrado'
  | 'metodo-no-permitido'
  | 'formato-no-aceptable'
  | 'conflicto-de-estado'
  | 'tipo-de-contenido-no-soportado'
  | 'archivo-demasiado-grande'
  | 'cuenta-bloqueada'
  | 'limite-reportes-excedido'
  | 'limite-de-peticiones-excedido'
  | 'error-interno'
  | 'servicio-no-disponible'
  | 'base-de-datos-no-disponible'

export interface ErrorApi {
  estado: number | null
  tipo: TipoProblema | null
  mensaje: string
  reintentable: boolean
  segundosParaReintentar: number | null
  problema: Problema | null
}

const MENSAJE_SIN_RED = 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.'
const MENSAJE_GENERICO = 'Algo falló de nuestro lado. Inténtalo otra vez en un momento.'

const TIPOS_QUE_NO_CIERRAN_SESION: ReadonlySet<string> = new Set([
  'credencial-invalida',
  'segundo-factor-requerido',
])

function esProblema(cuerpo: unknown): cuerpo is Problema {
  return typeof cuerpo === 'object' && cuerpo !== null && typeof (cuerpo as Problema).type === 'string'
}

export function tipoDeProblema(cuerpo: unknown): TipoProblema | null {
  if (!esProblema(cuerpo) || !cuerpo.type?.startsWith(PREFIJO_TIPO)) return null
  return cuerpo.type.slice(PREFIJO_TIPO.length) as TipoProblema
}

function leerRetryAfter(respuesta: Response): number | null {
  const valor = respuesta.headers.get('Retry-After')
  if (valor === null) return null
  const segundos = Number(valor)
  if (Number.isFinite(segundos)) return Math.max(0, segundos)
  const fecha = Date.parse(valor)
  return Number.isNaN(fecha) ? null : Math.max(0, Math.ceil((fecha - Date.now()) / 1000))
}

/** `respuesta` nula significa que ni siquiera hubo respuesta: fallo de red. */
export function normalizarError(respuesta: Response | null, cuerpo: unknown): ErrorApi {
  if (respuesta === null) {
    return {
      estado: null,
      tipo: null,
      mensaje: MENSAJE_SIN_RED,
      reintentable: true,
      segundosParaReintentar: null,
      problema: null,
    }
  }

  const problema = esProblema(cuerpo) ? cuerpo : null
  const estado = respuesta.status
  // El detail de un 500 es genérico a propósito en el backend, pero no se muestra por si deja de serlo.
  const detalle = estado !== 500 && typeof problema?.detail === 'string' ? problema.detail : null

  return {
    estado,
    tipo: tipoDeProblema(cuerpo),
    mensaje: detalle ?? MENSAJE_GENERICO,
    reintentable: estado >= 500 || estado === 429,
    segundosParaReintentar: estado === 429 || estado === 503 ? leerRetryAfter(respuesta) : null,
    problema,
  }
}

export const middlewareSesion: Middleware = {
  onRequest({ request }) {
    const token = sesion.token()
    if (token) request.headers.set('Authorization', `Bearer ${token}`)
    return request
  },
  async onResponse({ response }) {
    if (response.status !== 401 || !sesion.token()) return response
    const tipo = tipoDeProblema(await response.clone().json().catch(() => null))
    if (tipo === null || !TIPOS_QUE_NO_CIERRAN_SESION.has(tipo)) sesion.limpiar()
    return response
  },
}

// Sin `Accept: application/json` global: GET /api/sectores/geometria responde 404 si se lo mandan.
export function crearCliente(opciones: { baseUrl?: string; fetch?: typeof fetch } = {}) {
  const cliente = createClient<paths>({ baseUrl: opciones.baseUrl ?? '', fetch: opciones.fetch })
  cliente.use(middlewareSesion)
  return cliente
}

export const api = crearCliente()
