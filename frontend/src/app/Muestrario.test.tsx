import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Muestrario } from './Muestrario'

describe('Muestrario', () => {
  it('debeNombrarCadaEstadoConTextoYNoSoloConColor', () => {
    render(<Muestrario />)
    for (const texto of ['Sin servicio', 'Corte programado', 'Presión baja', 'Con servicio', 'Sin datos verificados']) {
      expect(screen.getByText(texto)).toBeInTheDocument()
    }
  })

  it('debeCambiarElAtributoDeTemaYRecordarlo', async () => {
    const usuario = userEvent.setup()
    render(<Muestrario />)

    await usuario.click(screen.getByRole('radio', { name: 'Oscuro' }))
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(window.localStorage.getItem('aguavigia.tema')).toBe('oscuro')

    await usuario.click(screen.getByRole('radio', { name: 'Como el sistema' }))
    expect(document.documentElement.dataset.theme).toBeUndefined()
    expect(window.localStorage.getItem('aguavigia.tema')).toBeNull()
  })
})
