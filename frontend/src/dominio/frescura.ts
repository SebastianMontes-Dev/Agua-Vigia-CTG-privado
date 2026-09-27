export const UMBRAL_SIN_VERIFICACION_MS = 24 * 60 * 60 * 1000
export const TEXTO_SIN_VERIFICACION_RECIENTE = 'Sin verificación reciente'

const UN_MINUTO_MS = 60 * 1000
const UNA_HORA_MS = 60 * UN_MINUTO_MS
const UN_DIA_MS = 24 * UNA_HORA_MS

export function describirEdad(desde: string | null | undefined, ahora: Date): string {
  if (!desde) {
    return 'Sin fecha de registro'
  }

  const timestampDesde = Date.parse(desde)
  if (Number.isNaN(timestampDesde)) {
    return 'Sin fecha de registro'
  }

  const diferencia = ahora.getTime() - timestampDesde

  if (diferencia < UN_MINUTO_MS) {
    return 'hace menos de un minuto'
  }

  if (diferencia < UNA_HORA_MS) {
    const minutos = Math.floor(diferencia / UN_MINUTO_MS)
    return `hace ${minutos} min`
  }

  if (diferencia < UN_DIA_MS) {
    const horas = Math.floor(diferencia / UNA_HORA_MS)
    const minutosRestantes = Math.floor((diferencia % UNA_HORA_MS) / UN_MINUTO_MS)
    if (minutosRestantes === 0) {
      return `hace ${horas} h`
    }
    return `hace ${horas} h ${minutosRestantes} min`
  }

  const dias = Math.floor(diferencia / UN_DIA_MS)
  if (dias === 1) {
    return 'hace 1 día'
  }
  return `hace ${dias} días`
}

export function sinVerificacionReciente(verificadoEn: string | null | undefined, ahora: Date): boolean {
  if (!verificadoEn) {
    return false
  }

  const timestampVerificado = Date.parse(verificadoEn)
  if (Number.isNaN(timestampVerificado)) {
    return false
  }

  const diferencia = ahora.getTime() - timestampVerificado
  return diferencia >= UMBRAL_SIN_VERIFICACION_MS
}
