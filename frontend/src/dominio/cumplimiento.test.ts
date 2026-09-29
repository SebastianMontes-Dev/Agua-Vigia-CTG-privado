import { describe, expect, it } from 'vitest'
import { cumplimientoDeEjemplo, cumplimientoDecimalDeEjemplo, serieDeEjemplo } from '../pruebas/datos/historia'
import { conclusionCumplimiento, diferenciaBreve, duracionAcumulada, limiteSerieCartagena, ordenarSerie, rangoSerieValido } from './cumplimiento'

describe('cumplimiento', () => {
  it('debeExpresarLaDiferenciaSinRepetirLasDuracionesNiElIndice', () => {
    expect(conclusionCumplimiento(cumplimientoDeEjemplo)).toBe('En total, los cortes cerrados duraron 2 horas y 30 minutos más de lo anunciado.')
    expect(conclusionCumplimiento({ ...cumplimientoDeEjemplo, desviacionSegundos: -3600 })).toBe('En total, los cortes cerrados duraron 1 hora menos de lo anunciado.')
    expect(conclusionCumplimiento({ ...cumplimientoDeEjemplo, desviacionSegundos: 0 })).toBe('En total, los cortes cerrados duraron lo anunciado.')
    expect(conclusionCumplimiento(cumplimientoDecimalDeEjemplo)).not.toContain('66,7%')
  })

  it('debeOmitirMinutosEnDuracionesDeUnDiaOMas', () => {
    expect(duracionAcumulada(131 * 86_400 + 17 * 3600 + 31 * 60)).toBe('131 días y 17 horas')
    expect(duracionAcumulada(86_400 + 59 * 60)).toBe('1 día')
    expect(duracionAcumulada(4 * 3600 + 30 * 60)).toBe('4 horas y 30 minutos')
    expect(duracionAcumulada(4 * 3600 + 29 * 60)).toBe('4 horas y 29 minutos')
  })

  it('debeResumirLaDiferenciaMensualEnHoras', () => {
    expect(diferenciaBreve(4 * 3600 + 30 * 60)).toBe('4 h 30 min más')
    expect(diferenciaBreve(-3600)).toBe('1 h menos')
    expect(diferenciaBreve(0)).toBe('Lo anunciado')
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
