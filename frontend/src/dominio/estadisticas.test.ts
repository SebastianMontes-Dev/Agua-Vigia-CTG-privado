import { describe, expect, it } from 'vitest'
import { estadisticasDeEjemplo } from '../pruebas/datos/historia'
import { disponibilidadEstadisticas, ordenarDias } from './estadisticas'

describe('estadísticas', () => {
  it('debeOrdenarLosSieteDiasYConservarLosCerosReales', () => {
    expect(ordenarDias(estadisticasDeEjemplo.cortesPorDiaDeSemana)).toEqual([
      { dia: 'Lunes', cortes: 2 }, { dia: 'Martes', cortes: 3 }, { dia: 'Miércoles', cortes: 7 },
      { dia: 'Jueves', cortes: 4 }, { dia: 'Viernes', cortes: 5 }, { dia: 'Sábado', cortes: 1 },
      { dia: 'Domingo', cortes: 0 },
    ])
  })

  it('debeDistinguirUnDiaAusenteDeUnCeroDevueltoPorLaAPI', () => {
    expect(ordenarDias({ Lunes: 0 })[1]?.cortes).toBeNull()
  })

  it('debeSumarLosCortesDeLosSieteDias', () => {
    expect(disponibilidadEstadisticas(estadisticasDeEjemplo).totalCortes).toBe(22)
  })

  it('debeTratarElCeroYLaAusenciaDeDuracionComoSinMedicion', () => {
    for (const duracionPromedioHoras of [0, null, undefined]) {
      expect(disponibilidadEstadisticas({ ...estadisticasDeEjemplo, duracionPromedioHoras }).duracionMedible).toBeNull()
    }
    expect(disponibilidadEstadisticas(estadisticasDeEjemplo).duracionMedible).toBe(5.4)
  })

  it('debeDeclararVacioGeneralSoloSinBarriosNiCortes', () => {
    const base = { sectoresMasAfectados: [], cortesPorDiaDeSemana: { Lunes: 0 }, duracionPromedioHoras: 0 }
    expect(disponibilidadEstadisticas(base).vacioGeneral).toBe(true)
    expect(disponibilidadEstadisticas({ ...base, sectoresMasAfectados: estadisticasDeEjemplo.sectoresMasAfectados }).vacioGeneral).toBe(false)
    expect(disponibilidadEstadisticas({ ...base, cortesPorDiaDeSemana: { Lunes: 1 } }).vacioGeneral).toBe(false)
  })
})
