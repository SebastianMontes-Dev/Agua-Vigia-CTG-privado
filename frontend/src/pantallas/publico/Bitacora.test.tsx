import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { bitacoraDeEjemplo } from '../../pruebas/datos/historia'
import { sectoresDeEjemplo } from '../../pruebas/datos/sectores'
import { Bitacora } from './Bitacora'

const estado = vi.hoisted(() => ({ error: false, fallo: false, vacio: false, sustentoAbierto: false }))

vi.mock('@tanstack/react-router', () => ({
  useSearch: () => ({}),
  useNavigate: () => vi.fn<() => void>(),
}))
vi.mock('../../app/ahora', () => ({ useAhora: () => new Date('2026-09-25T20:00:00Z') }))
vi.mock('../../app/datos', () => ({
  useListado: () => ({ sectores: sectoresDeEjemplo.sectores, lectura: { error: false }, reintentar: vi.fn<() => void>() }),
  useBitacora: () => estado.error
    ? { isPending: false, isError: true, error: { estado: 400 }, data: undefined }
    : estado.fallo
      ? { isPending: false, isError: true, error: { estado: 503 }, data: undefined, refetch: vi.fn<() => void>() }
      : { isPending: false, isError: false, error: null, hasNextPage: false, data: { pages: [{ eventos: estado.vacio ? [] : bitacoraDeEjemplo, paginacion: { total: estado.vacio ? 0 : 6 } }] } },
  useSustento: (_id: string, abierto: boolean) => {
    estado.sustentoAbierto = abierto
    return { isPending: !abierto, isError: false, data: abierto ? { pages: [{ ids: ['reporte-1'] }] } : undefined, hasNextPage: false }
  },
}))

describe('Bitácora', () => {
  afterEach(() => { estado.error = false; estado.fallo = false; estado.vacio = false; estado.sustentoAbierto = false })

  it('debeMostrarEstadoInformativoFuenteAusenteYOmitirSustentoEnCero', () => {
    render(<Bitacora />)
    expect(screen.getByText('Informativo')).toBeInTheDocument()
    expect(screen.getAllByText('Sin enlace a la fuente').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Sustentado por 5 reportes')).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: 'Ver referencias de sustento' })).toHaveLength(1)
    expect(screen.getByRole('link', { name: 'Boletín de Acuacar' })).toBeInTheDocument()
  })

  it('debeAbrirElSustentoSoloTrasLaAccion', async () => {
    render(<Bitacora />)
    expect(estado.sustentoAbierto).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Ver referencias de sustento' }))
    expect(estado.sustentoAbierto).toBe(true)
    expect(screen.getByText('reporte-1')).toBeInTheDocument()
  })

  it('debeSituarElErrorDeFiltroJuntoALosFiltros', () => {
    estado.error = true
    render(<Bitacora />)
    expect(screen.getByRole('alert')).toHaveTextContent('Revisa los filtros')
    expect(screen.queryByText('No pudimos consultar la bitácora')).not.toBeInTheDocument()
  })

  it('debeExplicarLaAusenciaDeEventosFiltrados', () => {
    estado.vacio = true
    render(<Bitacora />)
    expect(screen.getByText('No hay eventos con esos filtros')).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })

  it('debeOfrecerReintentarAnteUnErrorReal', () => {
    estado.fallo = true
    render(<Bitacora />)
    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos consultar la bitácora')
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
