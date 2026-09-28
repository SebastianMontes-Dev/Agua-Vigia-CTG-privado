import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import { cumplimientoDeEjemplo, serieDeEjemplo } from '../../pruebas/datos/historia'
import { sectoresDeEjemplo } from '../../pruebas/datos/sectores'
import { Cumplimiento } from './Cumplimiento'

const estado = vi.hoisted(() => ({ sinCortes: false, barrioDesconocido: false }))

vi.mock('@tanstack/react-router', () => ({
  useSearch: () => estado.barrioDesconocido ? { sector: 'desconocido' } : {},
  useNavigate: () => vi.fn<() => void>(),
}))
vi.mock('../../app/datos', () => ({
  useListado: () => ({ sectores: sectoresDeEjemplo.sectores, lectura: { error: false, listado: sectoresDeEjemplo }, reintentar: vi.fn<() => void>() }),
  useIndiceCumplimiento: () => estado.sinCortes
    ? { isPending: false, isError: true, error: { estado: 400 }, data: undefined }
    : { isPending: false, isError: false, error: null, data: cumplimientoDeEjemplo },
  useSerieCumplimiento: () => ({ isPending: false, isError: false, data: [serieDeEjemplo[2], serieDeEjemplo[0]] }),
}))

describe('Cumplimiento', () => {
  afterEach(() => { estado.sinCortes = false; estado.barrioDesconocido = false })

  it('debeExplicarDuracionesEIndiceAntesDeLaSerieSinRellenarHuecos', () => {
    render(<Cumplimiento />)
    expect(screen.getByText('En total, los cortes cerrados duraron 2 horas y 30 minutos más de lo anunciado.')).toBeInTheDocument()
    expect(screen.getByText('Índice de cumplimiento').nextElementSibling).toHaveTextContent('80%')
    expect(screen.getByText(/sobre 4 cortes/)).toBeInTheDocument()
    expect(screen.queryByText('marzo de 2026')).not.toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Datos de la serie mensual de cumplimiento' })).toBeInTheDocument()
  })

  it('debeExplicarUn400ComoAusenciaDeCortesCerrados', () => {
    estado.sinCortes = true
    render(<Cumplimiento />)
    expect(screen.getByText(/Aún no hay cortes cerrados para medir/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Comparación entre lo prometido y lo real' })).not.toBeInTheDocument()
  })

  it('debeDistinguirUnBarrioDesconocidoDeLaAusenciaDeCortes', () => {
    estado.sinCortes = true
    estado.barrioDesconocido = true
    render(<Cumplimiento />)
    expect(screen.getByText('No encontramos este barrio.')).toBeInTheDocument()
    expect(screen.queryByText(/Aún no hay cortes cerrados para medir/)).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Mes a mes' })).not.toBeInTheDocument()
  })
})
