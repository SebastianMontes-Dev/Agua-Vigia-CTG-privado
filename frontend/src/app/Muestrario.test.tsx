import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Muestrario } from './Muestrario'

describe('Muestrario', () => {
  it('debeNombrarCadaEstadoConTextoYNoSoloConColor', () => {
    render(<Muestrario />)
    for (const texto of ['Sin servicio', 'Corte programado', 'Presión baja', 'Con servicio', 'Sin datos verificados']) {
      expect(screen.getByText(texto)).toBeInTheDocument()
    }
  })

  it('debeMostrarLaAccionSoloEnLasCombinacionesPermitidas', () => {
    render(<Muestrario />)
    const combinaciones = within(screen.getByRole('region', { name: 'Acción y combinaciones permitidas' }))
    for (const texto of [
      'Cardenillo sobre papel',
      'Cardenillo sobre superficie',
      'Cardenillo sobre cardenillo suave',
      'Botón principal',
      'Latón: foco y selección',
    ]) {
      expect(combinaciones.getByText(texto)).toBeInTheDocument()
    }
  })

  it('noDebePresentarUnBarrioRealComoEjemplo', () => {
    render(<Muestrario />)
    expect(screen.queryByText(/Bocagrande/)).not.toBeInTheDocument()
    expect(screen.getByText('Texto de muestra, no describe ningún barrio real.')).toBeInTheDocument()
  })

  it('debeLlevarLaMarcaYUnSoloTitularDePagina', () => {
    render(<Muestrario />)
    expect(screen.getByText('AguaVigía')).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 1 }).map((h) => h.textContent)).toEqual(['Identidad del frontend'])
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
