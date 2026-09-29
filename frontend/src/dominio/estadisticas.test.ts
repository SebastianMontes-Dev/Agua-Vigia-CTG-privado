import { describe, expect, it } from 'vitest'
import { estadisticasDeEjemplo } from '../pruebas/datos/historia'
import { diasExtremos, disponibilidadEstadisticas, listaDeDias, ordenarDias, titularEstadisticas } from './estadisticas'

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

describe('respuesta de las estadísticas', () => {
  it('debeTitularConLaDuracionRedondeadaOConLaPreguntaSinMedicion', () => {
    expect(titularEstadisticas(24.2)).toBe('Un corte dura 24 horas en promedio')
    expect(titularEstadisticas(1.2)).toBe('Un corte dura 1 hora en promedio')
    expect(titularEstadisticas(0.5)).toBe('Un corte dura 30 minutos en promedio')
    expect(titularEstadisticas(null)).toBe('Cuándo hay cortes en Cartagena')
  })

  it('debeNombrarLosDiasExtremosConEmpates', () => {
    const dias = ordenarDias({ Lunes: 18, Martes: 20, Miércoles: 12, Jueves: 16, Viernes: 16, Sábado: 20, Domingo: 18 })
    expect(diasExtremos(dias, 'mas')).toEqual({ dias: ['Martes', 'Sábado'], cortes: 20 })
    expect(diasExtremos(dias, 'menos')).toEqual({ dias: ['Miércoles'], cortes: 12 })
    expect(listaDeDias(['Martes', 'Sábado'])).toBe('Martes y sábado')
  })

  it('noDebeNombrarExtremosSiTodosLosDiasEmpatan', () => {
    expect(diasExtremos(ordenarDias({ Lunes: 0, Martes: 0, Miércoles: 0, Jueves: 0, Viernes: 0, Sábado: 0, Domingo: 0 }), 'mas')).toBeNull()
  })
})
