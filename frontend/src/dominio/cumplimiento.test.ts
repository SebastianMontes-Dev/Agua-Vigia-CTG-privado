import { describe, expect, it } from 'vitest'
import { cumplimientoDeEjemplo, serieDeEjemplo } from '../pruebas/datos/historia'
import { conclusionCumplimiento, limiteSerieCartagena, ordenarSerie, rangoSerieValido } from './cumplimiento'

describe('cumplimiento', () => {
  it('expresa la diferencia y el índice en palabras', () => {
    expect(conclusionCumplimiento(cumplimientoDeEjemplo)).toBe('Prometieron 10 horas. Fueron 12 horas y media. Los cortes duraron 2 horas y media más de lo prometido: 80% de cumplimiento')
    expect(conclusionCumplimiento({ ...cumplimientoDeEjemplo, desviacionSegundos: -3600 })).toContain('terminaron 1 hora antes')
  })

  it('ordena la serie sin inventar meses vacíos', () => {
    const serie = ordenarSerie([serieDeEjemplo[2]!, serieDeEjemplo[0]!])
    expect(serie.map((punto) => punto.periodo)).toEqual(['2026-04', '2026-06'])
  })

  it('convierte días de Cartagena para el rango inclusivo de la serie', () => {
    expect(limiteSerieCartagena('2026-09-01')).toBe('2026-09-01T05:00:00.000Z')
    expect(limiteSerieCartagena('2026-09-01', true)).toBe('2026-09-02T04:59:59.999Z')
    expect(rangoSerieValido('2026-09-02', '2026-09-01')).toBe(false)
  })
})
