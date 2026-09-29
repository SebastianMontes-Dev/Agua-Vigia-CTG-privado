import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import { cumplimientoDeEjemplo, serieDeEjemplo } from '../../pruebas/datos/historia'
import { sectoresDeEjemplo } from '../../pruebas/datos/sectores'
import { Cumplimiento } from './Cumplimiento'

const estado = vi.hoisted(() => ({ sinCortes: false, barrioDesconocido: false, sinSerie: false }))

vi.mock('@tanstack/react-router', () => ({
  useSearch: () => estado.barrioDesconocido ? { sector: 'desconocido' } : {},
  useNavigate: () => vi.fn<() => void>(),
}))
vi.mock('../../app/datos', () => ({
  useListado: () => ({ sectores: sectoresDeEjemplo.sectores, lectura: { error: false, listado: sectoresDeEjemplo }, reintentar: vi.fn<() => void>() }),
  useIndiceCumplimiento: () => estado.sinCortes
    ? { isPending: false, isSuccess: false, isError: true, error: { estado: 400 }, data: undefined }
    : { isPending: false, isSuccess: true, isError: false, error: null, data: cumplimientoDeEjemplo },
  useSerieCumplimiento: () => estado.sinSerie
    ? { isPending: false, isError: true, data: undefined, refetch: vi.fn<() => void>() }
    : { isPending: false, isError: false, data: [serieDeEjemplo[2], serieDeEjemplo[0]] },
}))

describe('Cumplimiento', () => {
  afterEach(() => { estado.sinCortes = false; estado.barrioDesconocido = false; estado.sinSerie = false })

  it('debeTitularConElVeredictoYPromediarPorCorte', () => {
    render(<Cumplimiento />)
    // 9000 s de más entre 10 cortes (6 + 4 en la serie): 15 minutos por corte.
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Los cortes duran más de lo anunciado')
    expect(screen.getByText(/10 cortes cerrados/)).toBeInTheDocument()
    expect(screen.getByText('15 min más')).toBeInTheDocument()
    expect(screen.getByText(/Índice 80/)).toBeInTheDocument()
    expect(screen.queryByText('marzo de 2026')).not.toBeInTheDocument()
  })

  it('noDebeInventarElPromedioSinLaSerie', () => {
    estado.sinSerie = true
    render(<Cumplimiento />)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Lo prometido y lo que duró')
    expect(screen.getByText('En total, los cortes cerrados duraron 2 horas y 30 minutos más de lo anunciado.')).toBeInTheDocument()
    expect(screen.queryByText(/cortes cerrados ·/)).not.toBeInTheDocument()
  })

  it('debeExplicarUn400ComoAusenciaDeCortesCerrados', () => {
    estado.sinCortes = true
    render(<Cumplimiento />)
    expect(screen.getByText(/Aún no hay cortes cerrados para medir/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Promedio por corte' })).not.toBeInTheDocument()
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
