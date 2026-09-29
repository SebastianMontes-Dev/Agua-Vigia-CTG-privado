import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CodigoQr, svgDeQr, trazadoDeQr } from './CodigoQr'

describe('CodigoQr', () => {
  it('debeDibujarUnModuloPorCadaPuntoOscuro', () => {
    expect(trazadoDeQr([[true, false], [false, true]])).toBe('M0 0h1v1h-1zM1 1h1v1h-1z')
  })

  it('debeGenerarLaImagenLocalmenteSinLlamarAServiciosExternos', () => {
    render(<CodigoQr contenido="otpauth://totp/AguaVigia:ana@example.com?secret=JBSWY3DPEHPK3PXP&issuer=AguaVigia" titulo="Código QR de prueba" />)
    const imagen = screen.getByRole('img', { name: 'Código QR de prueba' })
    expect(imagen.getAttribute('src')).toMatch(/^data:image\/svg\+xml/)
    expect(decodeURIComponent(imagen.getAttribute('src') ?? '')).toContain('<path d="M')
  })

  it('debeUsarModulosOscurosSobreFondoClaroEnCualquierTema', () => {
    const svg = svgDeQr([[true]])
    expect(svg).toContain('fill="white"')
    expect(svg).toContain('fill="black"')
  })
})
