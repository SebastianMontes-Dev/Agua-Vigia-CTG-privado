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

/** `identidad.md` §4.1: el titular es la respuesta. Sin duración medida, titula la pregunta. */
export function titularEstadisticas(duracionHoras: number | null): string {
  if (duracionHoras === null) return 'Cuándo hay cortes en Cartagena'
  if (duracionHoras < 1) {
    const minutos = Math.max(1, Math.round(duracionHoras * 60))
    return `Un corte dura ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'} en promedio`
  }
  const horas = Math.round(duracionHoras)
  return `Un corte dura ${horas} ${horas === 1 ? 'hora' : 'horas'} en promedio`
}

export interface DiasExtremos {
  dias: string[]
  cortes: number
}

/** Los días con más (o menos) cortes, con empates. Nulo si no hay ningún día con dato o si todos empatan. */
export function diasExtremos(dias: readonly DiaConCortes[], criterio: 'mas' | 'menos'): DiasExtremos | null {
  const conDato = dias.filter((dia): dia is DiaConCortes & { cortes: number } => dia.cortes !== null)
  if (!conDato.length) return null
  const valores = conDato.map((dia) => dia.cortes)
  const objetivo = criterio === 'mas' ? Math.max(...valores) : Math.min(...valores)
  if (Math.max(...valores) === Math.min(...valores)) return null
  return { dias: conDato.filter((dia) => dia.cortes === objetivo).map((dia) => dia.dia), cortes: objetivo }
}

export function listaDeDias(dias: readonly string[]): string {
  const nombres = dias.map((dia, indice) => (indice === 0 ? dia : dia.toLowerCase()))
  return nombres.length > 1 ? `${nombres.slice(0, -1).join(', ')} y ${nombres.at(-1)}` : nombres[0] ?? ''
}
