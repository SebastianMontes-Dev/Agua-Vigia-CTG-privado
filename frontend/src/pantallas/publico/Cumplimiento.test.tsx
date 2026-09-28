import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import { cumplimientoDeEjemplo, serieDeEjemplo } from '../../pruebas/datos/historia'
import { sectoresDeEjemplo } from '../../pruebas/datos/sectores'
import { Cumplimiento } from './Cumplimiento'

const estado = vi.hoisted(() => ({ sinCortes: false }))

vi.mock('@tanstack/react-router', () => ({
  useSearch: () => ({}),
  useNavigate: () => vi.fn<() => void>(),
}))
vi.mock('../../app/datos', () => ({
  useListado: () => ({ sectores: sectoresDeEjemplo.sectores, lectura: { error: false }, reintentar: vi.fn<() => void>() }),
  useIndiceCumplimiento: () => estado.sinCortes
    ? { isPending: false, isError: true, error: { estado: 400 }, data: undefined }
    : { isPending: false, isError: false, error: null, data: cumplimientoDeEjemplo },
  useSerieCumplimiento: () => ({ isPending: false, isError: false, data: [serieDeEjemplo[2], serieDeEjemplo[0]] }),
}))

describe('Cumplimiento', () => {
  afterEach(() => { estado.sinCortes = false })

  it('explica duraciones e índice antes de la serie y no rellena huecos', () => {
    render(<Cumplimiento />)
    expect(screen.getByText(/Prometieron 10 horas. Fueron 12 horas y media/)).toBeInTheDocument()
    expect(screen.getByText('sobre 4 cortes')).toBeInTheDocument()
    expect(screen.queryByText('marzo de 2026')).not.toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Datos de la serie mensual de cumplimiento' })).toBeInTheDocument()
  })

  it('explica un 400 como ausencia de cortes cerrados', () => {
    estado.sinCortes = true
    render(<Cumplimiento />)
    expect(screen.getByText(/Aún no hay cortes cerrados para medir/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Comparación entre lo prometido y lo real' })).not.toBeInTheDocument()
  })
})
