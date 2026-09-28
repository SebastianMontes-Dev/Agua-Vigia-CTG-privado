import { describe, expect, it } from 'vitest'
import { cumplimientoDeEjemplo, cumplimientoDecimalDeEjemplo, serieDeEjemplo } from '../pruebas/datos/historia'
import { conclusionCumplimiento, limiteSerieCartagena, ordenarSerie, rangoSerieValido } from './cumplimiento'

describe('cumplimiento', () => {
  it('debeExpresarLaDiferenciaYElIndiceEnPalabras', () => {
    expect(conclusionCumplimiento(cumplimientoDeEjemplo)).toBe('En total, los cortes cerrados tenían anunciados 10 horas y duraron 12 horas y media. Los cortes duraron 2 horas y media más de lo prometido: 80% de cumplimiento')
    expect(conclusionCumplimiento({ ...cumplimientoDeEjemplo, desviacionSegundos: -3600 })).toContain('terminaron 1 hora antes')
    expect(conclusionCumplimiento(cumplimientoDecimalDeEjemplo)).toContain('66,7% de cumplimiento')
  })

  it('debeOrdenarLaSerieSinInventarMesesVacios', () => {
    const serie = ordenarSerie([serieDeEjemplo[2]!, serieDeEjemplo[0]!])
    expect(serie.map((punto) => punto.periodo)).toEqual(['2026-04', '2026-06'])
  })

  it('debeConvertirDiasDeCartagenaParaElRangoInclusivo', () => {
    expect(limiteSerieCartagena('2026-09-01')).toBe('2026-09-01T05:00:00.000Z')
    expect(limiteSerieCartagena('2026-09-01', true)).toBe('2026-09-02T04:59:59.999Z')
    expect(rangoSerieValido('2026-09-02', '2026-09-01')).toBe(false)
  })
})
