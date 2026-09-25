import type { components } from '../api/generado/esquema'

export type EstadoServicio = NonNullable<components['schemas']['SectorRespuesta']['estado']>

export interface PresentacionEstado {
  clave: EstadoServicio | 'SIN_DATOS'
  texto: string
  variableColor: string | null
}

const PRESENTACIONES: Record<EstadoServicio, PresentacionEstado> = {
  SIN_SERVICIO: { clave: 'SIN_SERVICIO', texto: 'Sin servicio', variableColor: '--estado-sin-servicio' },
  CORTE_PROGRAMADO: {
    clave: 'CORTE_PROGRAMADO',
    texto: 'Corte programado',
    variableColor: '--estado-corte-programado',
  },
  PRESION_BAJA: { clave: 'PRESION_BAJA', texto: 'Presión baja', variableColor: '--estado-presion-baja' },
  CON_SERVICIO: { clave: 'CON_SERVICIO', texto: 'Con servicio', variableColor: '--estado-con-servicio' },
}

// Sin dato no hay color: se pinta con la trama. Mostrarlo verde sería el falso positivo de ADR-014.
const SIN_DATOS: PresentacionEstado = { clave: 'SIN_DATOS', texto: 'Sin datos verificados', variableColor: null }

/** Orden de severidad de ADR-061, de más grave a menos. */
export const ORDEN_ESTADOS: readonly EstadoServicio[] = [
  'SIN_SERVICIO',
  'CORTE_PROGRAMADO',
  'PRESION_BAJA',
  'CON_SERVICIO',
]

export function presentarEstado(estado: EstadoServicio | null | undefined): PresentacionEstado {
  return estado ? PRESENTACIONES[estado] : SIN_DATOS
}
