import {
  TEXTO_SIN_VERIFICACION_RECIENTE,
  UMBRAL_SIN_VERIFICACION_MS,
  describirEdad,
  sinVerificacionReciente,
} from './frescura'

describe('frescura', () => {
  const AHORA = new Date('2026-09-25T20:00:00Z')

  describe('constantes', () => {
    it('debeTenerElUmbralDeVeinticuatroHorasEnMilisegundos', () => {
      expect(UMBRAL_SIN_VERIFICACION_MS).toBe(86_400_000)
    })

    it('debeTenerElTextoExactoDeSinVerificacionReciente', () => {
      expect(TEXTO_SIN_VERIFICACION_RECIENTE).toBe('Sin verificación reciente')
    })
  })

  describe('describirEdad', () => {
    it.each([
      { desde: '2026-09-25T20:00:00Z', esperado: 'hace menos de un minuto' },
      { desde: '2026-09-25T19:59:00.001Z', esperado: 'hace menos de un minuto' },
      { desde: '2026-09-25T20:05:00Z', esperado: 'hace menos de un minuto' },
      { desde: '2026-09-25T19:59:00Z', esperado: 'hace 1 min' },
      { desde: '2026-09-25T19:01:00Z', esperado: 'hace 59 min' },
      { desde: '2026-09-25T19:00:00Z', esperado: 'hace 1 h' },
      { desde: '2026-09-25T16:48:00Z', esperado: 'hace 3 h 12 min' },
      { desde: '2026-09-24T20:00:00.001Z', esperado: 'hace 23 h 59 min' },
      { desde: '2026-09-24T20:00:00Z', esperado: 'hace 1 día' },
      { desde: '2026-09-22T10:00:00Z', esperado: 'hace 3 días' },
      { desde: null, esperado: 'Sin fecha de registro' },
      { desde: undefined, esperado: 'Sin fecha de registro' },
      { desde: 'no-es-fecha', esperado: 'Sin fecha de registro' },
    ])('debeDescribirEdadCorrectamente para "$desde"', ({ desde, esperado }) => {
      expect(describirEdad(desde, AHORA)).toBe(esperado)
    })
  })

  describe('sinVerificacionReciente', () => {
    it.each([
      { verificadoEn: '2026-09-24T20:00:00Z', esperado: true },
      { verificadoEn: '2026-09-24T20:00:00.001Z', esperado: false },
      { verificadoEn: '2026-09-20T12:00:00Z', esperado: true },
      { verificadoEn: '2026-09-25T21:00:00Z', esperado: false },
      { verificadoEn: null, esperado: false },
      { verificadoEn: undefined, esperado: false },
      { verificadoEn: 'no-es-fecha', esperado: false },
    ])('debeDeterminarSinVerificacionReciente para "$verificadoEn"', ({ verificadoEn, esperado }) => {
      expect(sinVerificacionReciente(verificadoEn, AHORA)).toBe(esperado)
    })
  })
})
