import { formatearNumero, formatearPorcentaje } from './formato'

const normalizar = (s: string): string => s.replace(/[  ]/g, ' ')

describe('formato', () => {
  describe('formatearNumero', () => {
    it('debeFormatearNumeroEnteroConPuntosDeMiles', () => {
      expect(normalizar(formatearNumero(1234567))).toBe('1.234.567')
    })

    it('debeFormatearNumeroDecimalConComaYUnDecimalMaximo', () => {
      expect(normalizar(formatearNumero(1234.56, 1))).toBe('1.234,6')
      expect(normalizar(formatearNumero(0.5, 1))).toBe('0,5')
    })

    it('debeRespetarCeroDecimalesMinimosCuandoEsEntero', () => {
      expect(normalizar(formatearNumero(3, 1))).toBe('3')
    })
  })

  describe('formatearPorcentaje', () => {
    it('debeFormatearPorcentajeConComaYUnDecimal', () => {
      expect(normalizar(formatearPorcentaje(87.3))).toBe('87,3%')
    })

    it('debeFormatearExtremosSinDecimalesInnecesarios', () => {
      expect(normalizar(formatearPorcentaje(100))).toBe('100%')
      expect(normalizar(formatearPorcentaje(0))).toBe('0%')
    })
  })
})
