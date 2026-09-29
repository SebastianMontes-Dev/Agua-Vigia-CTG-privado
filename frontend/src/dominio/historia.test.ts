import { describe, expect, it } from 'vitest'
import { imagenDeBitacora, limiteDiaCartagena, rangoDiasValido } from './historia'

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
