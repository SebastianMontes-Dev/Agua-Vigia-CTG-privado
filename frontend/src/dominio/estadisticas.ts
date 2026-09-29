export const DIAS_SEMANA = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'] as const

export interface DiaConCortes {
  dia: typeof DIAS_SEMANA[number]
  cortes: number | null
}

export function ordenarDias(cortesPorDia: Record<string, number> | undefined): DiaConCortes[] {
  return DIAS_SEMANA.map((dia) => ({ dia, cortes: cortesPorDia?.[dia] ?? null }))
}

interface DatosDisponibilidad {
  sectoresMasAfectados?: readonly unknown[]
  cortesPorDiaDeSemana?: Record<string, number>
  duracionPromedioHoras?: number | null
}

export function disponibilidadEstadisticas(datos: DatosDisponibilidad) {
  const totalCortes = Object.values(datos.cortesPorDiaDeSemana ?? {}).reduce((total, cantidad) => total + cantidad, 0)
  // El backend devuelve 0.0 cuando todavía no hay cortes cerrados para calcular la media.
  const duracionMedible = datos.duracionPromedioHoras != null && datos.duracionPromedioHoras > 0
    ? datos.duracionPromedioHoras : null
  return {
    totalCortes,
    duracionMedible,
    vacioGeneral: (datos.sectoresMasAfectados?.length ?? 0) === 0 && totalCortes === 0,
  }
}
