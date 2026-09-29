import { describe, expect, it } from 'vitest'
import { descripcionLegible, imagenDeBitacora, limiteDiaCartagena, rangoDiasValido } from './historia'
import { claveDiaCartagena, etiquetaDia } from './tiempo'

describe('días de la bitácora', () => {
  const ahora = new Date('2026-09-25T20:00:00Z')

  it('debeAgruparPorElDiaDeCartagenaYNoPorElDeUtc', () => {
    expect(claveDiaCartagena('2026-09-26T03:00:00Z')).toBe('2026-09-25')
  })

  it('debeNombrarHoyAyerYLosDemasDiasConSuDiaDeLaSemana', () => {
    expect(etiquetaDia('2026-09-25T12:00:00Z', ahora)).toBe('Hoy')
    expect(etiquetaDia('2026-09-24T14:00:00Z', ahora)).toBe('Ayer')
    expect(etiquetaDia('2026-09-21T14:00:00Z', ahora)).toMatch(/^Lunes,? 21 de septiembre$/)
  })
})

describe('historia pública', () => {
  it('convierte días de Cartagena a límites UTC inclusivo y exclusivo', () => {
    expect(limiteDiaCartagena('2026-09-01')).toBe('2026-09-01T05:00:00Z')
    expect(limiteDiaCartagena('2026-09-01', true)).toBe('2026-09-02T05:00:00Z')
    expect(limiteDiaCartagena('2026-12-31', true)).toBe('2027-01-01T05:00:00Z')
  })

  it('rechaza fechas imposibles e intervalos invertidos', () => {
    expect(limiteDiaCartagena('2026-02-30')).toBeNull()
    expect(rangoDiasValido('2026-09-02', '2026-09-01')).toBe(false)
    expect(rangoDiasValido('2026-09-01', '2026-09-01')).toBe(true)
  })

  it('pasa las portadas de Acuacar por el proxy local', () => {
    expect(imagenDeBitacora('https://www.acuacar.com/wp-content/uploads/2026/foto.jpg')).toBe('/acuacar-media/2026/foto.jpg')
    expect(imagenDeBitacora(null)).toBeNull()
  })
})

describe('descripciones de la bitácora', () => {
  const nombreDe = (id: string) => ({ 'arroyo-grande': 'Arroyo Grande' } as Record<string, string>)[id]

  it('debeEscribirElEstadoYElBarrioEnPalabras', () => {
    expect(descripcionLegible("3 reportes confirmaron SIN_SERVICIO en 'arroyo-grande'", nombreDe))
      .toBe('3 reportes confirmaron sin servicio en Arroyo Grande')
  })

  it('debeConservarUnIdQueNoCorrespondeANingunBarrio', () => {
    expect(descripcionLegible("en 'no-existe'", nombreDe)).toBe("en 'no-existe'")
  })

  it('debeCambiarSoloElNombreEnMayusculasDelBarrioDelEvento', () => {
    expect(descripcionLegible('Servicio normalizado en el sector SAN ANTONIO.', nombreDe, { original: 'SAN ANTONIO', legible: 'San Antonio' }))
      .toBe('Servicio normalizado en el sector San Antonio.')
    expect(descripcionLegible('Corte en EL BOSQUE', nombreDe, { original: 'Manga', legible: 'Manga' })).toBe('Corte en EL BOSQUE')
  })
})
