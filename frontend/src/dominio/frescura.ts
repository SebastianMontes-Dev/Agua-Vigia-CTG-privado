export const UMBRAL_VERIFICACION_MS = 24 * 60 * 60_000

/**
 * ADR-073: tras 24 horas sin una nueva verificación se advierte, sin tocar el estado publicado.
 * Sin fecha no hay nada que advertir: ese caso ya se presenta como «Sin datos verificados».
 */
export function sinVerificacionReciente(verificadoEn: string | null | undefined, ahora: Date): boolean {
  if (!verificadoEn) return false
  return ahora.getTime() - new Date(verificadoEn).getTime() > UMBRAL_VERIFICACION_MS
}
