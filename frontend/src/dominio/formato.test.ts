import {
  formatearHora,
  formatearMomento,
  formatearNumero,
  formatearPorcentaje,
} from './formato'

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

  describe('formatearHora', () => {
    it('debeFormatearHoraEnFormatoDeDoceHorasParaBogota', () => {
      expect(normalizar(formatearHora('2026-09-25T23:10:00Z'))).toBe('6:10 p. m.')
    })

    it('debeFormatearMedianocheCorrectamenteEnBogota', () => {
      expect(normalizar(formatearHora('2026-09-26T05:00:00Z'))).toBe('12:00 a. m.')
    })

    it('debeDevolverSinFechaDeRegistroCuandoEsNuloOUndefined', () => {
      expect(formatearHora(null)).toBe('Sin fecha de registro')
      expect(formatearHora(undefined)).toBe('Sin fecha de registro')
      expect(formatearHora('fecha-invalida')).toBe('Sin fecha de registro')
    })
  })

  describe('formatearMomento', () => {
    const ahora = new Date('2026-09-25T20:00:00Z')

    it('debeMostrarSoloLaHoraCuandoEsElMismoDiaEnBogota', () => {
      // 2026-09-26T03:00:00Z es 2026-09-25 a las 10:00 p. m. en Bogotá
      expect(normalizar(formatearMomento('2026-09-26T03:00:00Z', ahora))).toBe('10:00 p. m.')
      // 2026-09-25T23:10:00Z es 2026-09-25 a las 6:10 p. m. en Bogotá
      expect(normalizar(formatearMomento('2026-09-25T23:10:00Z', ahora))).toBe('6:10 p. m.')
    })

    it('debeMostrarDiaMesYHoraCuandoEsOtroDiaDelMismoAno', () => {
      expect(normalizar(formatearMomento('2026-09-24T23:10:00Z', ahora))).toBe('24 de sept, 6:10 p. m.')
    })

    it('debeMostrarDiaMesAnoYHoraCuandoEsOtroAno', () => {
      expect(normalizar(formatearMomento('2025-12-31T23:10:00Z', ahora))).toBe('31 de dic de 2025, 6:10 p. m.')
    })

    it('debeDevolverSinFechaDeRegistroCuandoEsNuloOUndefined', () => {
      expect(formatearMomento(null, ahora)).toBe('Sin fecha de registro')
      expect(formatearMomento(undefined, ahora)).toBe('Sin fecha de registro')
      expect(formatearMomento('fecha-invalida', ahora)).toBe('Sin fecha de registro')
    })
  })
})
