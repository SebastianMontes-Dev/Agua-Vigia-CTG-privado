import type { components } from '../api/generado/esquema'
import { duracionEnPalabras } from './tiempo'

export type Corte = components['schemas']['CorteRespuesta']

// ADR-006: solo se atribuye lo que el origen sostiene. INGESTA_IA es una propuesta del colector que un veedor aprobó.
const ORIGENES: Record<string, string> = {
  OFICIAL_ACUACAR: 'Aviso oficial de Acuacar',
  VEEDOR: 'Registrado por un veedor',
  INGESTA_IA: 'Tomado de una publicación y aprobado por un veedor',
}

export function origenEnPalabras(origen: string | undefined): string | null {
  return origen ? ORIGENES[origen] ?? null : null
}

export function estaAbierto(corte: Corte): boolean {
  return !corte.finReal && corte.estado !== 'RESTABLECIDO'
}

export function estaCerrado(corte: Corte): boolean {
  return Boolean(corte.inicio && corte.finPrometido && corte.finReal)
}

export function promesaVencida(corte: Corte, ahora: Date): boolean {
  return estaAbierto(corte) && Boolean(corte.finPrometido) && new Date(corte.finPrometido!).getTime() < ahora.getTime()
}

export interface Comparacion {
  prometidoMs: number
  realMs: number
  prometido: string
  real: string
  diferencia: string
}

/** Guía §5.2: la diferencia se dice en palabras; «Terminó X antes» cuando duró menos de lo prometido. */
export function compararCorte(corte: Corte): Comparacion | null {
  if (!estaCerrado(corte)) return null
  const inicio = new Date(corte.inicio!).getTime()
  const prometidoMs = new Date(corte.finPrometido!).getTime() - inicio
  const realMs = new Date(corte.finReal!).getTime() - inicio
  if (prometidoMs <= 0 || realMs < 0) return null
  const exceso = realMs - prometidoMs
  const diferencia = Math.abs(exceso) < 15 * 60_000 ? 'Duró lo prometido'
    : exceso > 0 ? `Se pasó ${duracionEnPalabras(exceso)}` : `Terminó ${duracionEnPalabras(exceso)} antes`
  return { prometidoMs, realMs, prometido: duracionEnPalabras(prometidoMs), real: duracionEnPalabras(realMs), diferencia }
}
