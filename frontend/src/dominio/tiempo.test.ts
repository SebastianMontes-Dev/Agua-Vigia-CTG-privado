import { diaRelativo, duracionEnPalabras, formatearHora, haceCuanto, momento, partirHora } from './tiempo'

// 2026-09-26 10:00 en Cartagena = 15:00 UTC.
const AHORA = new Date('2026-09-26T15:00:00Z')

describe('tiempo', () => {
  it('debeMostrarLaHoraDeCartagenaYNoLaDelNavegador', () => {
    expect(formatearHora('2026-09-26T11:10:00Z')).toBe('6:10 a. m.')
    expect(formatearHora('2026-09-26T19:00:00Z')).toBe('2:00 p. m.')
  })

  it('debePartirLaHoraEnCifraYPeriodo', () => {
    expect(partirHora('2026-09-26T11:10:00Z')).toEqual({ cifra: '6:10', periodo: 'a. m.' })
  })

  it('debeUsarElCalendarioDeCartagenaParaDecirHoyAyerYMañana', () => {
    // 03:00 UTC del 26 son las 10 p. m. del 25 en Cartagena.
    expect(diaRelativo('2026-09-26T03:00:00Z', AHORA)).toBe('ayer')
    expect(diaRelativo('2026-09-26T06:00:00Z', AHORA)).toBe('hoy')
    expect(diaRelativo('2026-09-27T16:00:00Z', AHORA)).toBe('mañana')
    expect(diaRelativo('2026-07-24T16:00:00Z', AHORA)).toBe('24 de julio')
    expect(diaRelativo('2025-07-24T16:00:00Z', AHORA)).toBe('24 de julio de 2025')
  })

  it('debeDecirElMomentoConSuDiaCuandoNoEsHoy', () => {
    expect(momento('2026-09-26T14:29:00Z', AHORA)).toBe('a las 9:29 a. m.')
    expect(momento('2026-07-24T16:00:00Z', AHORA)).toBe('el 24 de julio a las 11:00 a. m.')
  })

  it('debeDecirHaceCuantoSinRedondearHaciaCero', () => {
    expect(haceCuanto('2026-09-26T14:59:30Z', AHORA)).toBe('hace menos de un minuto')
    expect(haceCuanto('2026-09-26T14:56:00Z', AHORA)).toBe('hace 4 min')
    expect(haceCuanto('2026-09-26T09:30:00Z', AHORA)).toBe('hace 5 h 30 min')
    expect(haceCuanto('2026-09-26T13:00:00Z', AHORA)).toBe('hace 2 h')
    expect(haceCuanto('2026-09-24T15:00:00Z', AHORA)).toBe('hace 2 días')
  })

  it('debeEscribirLaDuracionEnPalabras', () => {
    const hora = 3_600_000
    expect(duracionEnPalabras(4.5 * hora)).toBe('4 horas y media')
    expect(duracionEnPalabras(2 * hora)).toBe('2 horas')
    expect(duracionEnPalabras(1.25 * hora)).toBe('1 hora y 15 minutos')
    expect(duracionEnPalabras(45 * 60_000)).toBe('45 minutos')
    expect(duracionEnPalabras(1 * 60_000)).toBe('1 minuto')
    expect(duracionEnPalabras(72 * hora)).toBe('3 días')
  })
})
