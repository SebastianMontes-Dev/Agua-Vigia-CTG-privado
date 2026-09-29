export const TIPOS_BITACORA = [
  'CORTE_ANUNCIADO',
  'CORTE_RESTABLECIDO',
  'CORTE_CONFIRMADO_POR_CIUDADANOS',
  'CORTE_DETECTADO_POR_INGESTA',
] as const

export type TipoBitacora = typeof TIPOS_BITACORA[number]

export const NOMBRE_TIPO: Record<TipoBitacora, string> = {
  CORTE_ANUNCIADO: 'Corte anunciado',
  CORTE_RESTABLECIDO: 'Corte restablecido',
  CORTE_CONFIRMADO_POR_CIUDADANOS: 'Confirmado por ciudadanos',
  CORTE_DETECTADO_POR_INGESTA: 'Detectado en boletín',
}

/** Nombre de cada tipo como pestaña de filtro: el plural de lo que se ve en la lista. */
export const FILTRO_TIPO: Record<TipoBitacora, string> = {
  CORTE_ANUNCIADO: 'Anunciados',
  CORTE_RESTABLECIDO: 'Restablecidos',
  CORTE_CONFIRMADO_POR_CIUDADANOS: 'Confirmados por vecinos',
  CORTE_DETECTADO_POR_INGESTA: 'En boletines',
}

const ESTADO_EN_TEXTO: Record<string, string> = {
  SIN_SERVICIO: 'sin servicio',
  CON_SERVICIO: 'con servicio',
  PRESION_BAJA: 'presión baja',
  CORTE_PROGRAMADO: 'corte programado',
}

/**
 * Las descripciones las arma el backend para el registro y traen el enum y el id del barrio
 * («confirmaron SIN_SERVICIO en 'arroyo-grande'»). Se muestran en palabras sin cambiar lo que dicen.
 */
export function descripcionLegible(
  texto: string,
  nombreDe: (sectorId: string) => string | undefined,
  barrio?: { original: string; legible: string },
): string {
  const legible = texto
    .replace(/\b(SIN_SERVICIO|CON_SERVICIO|PRESION_BAJA|CORTE_PROGRAMADO)\b/g, (estado) => ESTADO_EN_TEXTO[estado] ?? estado)
    .replace(/'([a-z0-9]+(?:-[a-z0-9]+)*)'/g, (original, id: string) => nombreDe(id) ?? original)
  // El nombre oficial del barrio viene en mayúsculas («en el sector SAN ANTONIO»); solo se cambia el del propio evento.
  return barrio && barrio.original.length > 2 && barrio.original === barrio.original.toUpperCase()
    ? legible.split(barrio.original).join(barrio.legible)
    : legible
}

export function esTipoBitacora(valor: unknown): valor is TipoBitacora {
  return typeof valor === 'string' && TIPOS_BITACORA.some((tipo) => tipo === valor)
}

function diaValido(dia: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dia)) return false
  const fecha = new Date(`${dia}T00:00:00Z`)
  return !Number.isNaN(fecha.getTime()) && fecha.toISOString().slice(0, 10) === dia
}

/** Los días elegidos son de Cartagena (UTC−5); `hasta` es el inicio exclusivo del día siguiente. */
export function limiteDiaCartagena(dia: string, siguiente = false): string | null {
  if (!diaValido(dia)) return null
  const inicio = Date.parse(`${dia}T05:00:00Z`)
  return new Date(inicio + (siguiente ? 86_400_000 : 0)).toISOString().replace('.000Z', 'Z')
}

export function rangoDiasValido(desde?: string, hasta?: string): boolean {
  if (desde && !diaValido(desde)) return false
  if (hasta && !diaValido(hasta)) return false
  return !desde || !hasta || desde <= hasta
}

const PREFIJO_IMAGEN = 'https://www.acuacar.com/wp-content/uploads/'

export function imagenDeBitacora(url?: string | null): string | null {
  if (!url) return null
  return url.startsWith(PREFIJO_IMAGEN) ? `/acuacar-media/${url.slice(PREFIJO_IMAGEN.length)}` : url
}
