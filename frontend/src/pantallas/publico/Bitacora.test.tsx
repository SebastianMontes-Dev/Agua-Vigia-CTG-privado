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
// 15:00 en Cartagena: los eventos del 25 son «Hoy» y el del 24, «Ayer».
vi.mock('../../app/ahora', () => ({ useAhora: () => new Date('2026-09-25T20:00:00Z') }))
vi.mock('../../app/datos', () => ({
  useListado: () => ({ sectores: sectoresDeEjemplo.sectores, lectura: { error: false }, reintentar: vi.fn<() => void>() }),
  useBitacora: () => estado.error
    ? { isPending: false, isError: true, error: { estado: 400 }, data: undefined }
    : estado.fallo
      ? { isPending: false, isError: true, error: { estado: 503 }, data: undefined, refetch: vi.fn<() => void>() }
      : { isPending: false, isError: false, error: null, hasNextPage: false, data: { pages: [{ eventos: estado.vacio ? [] : bitacoraDeEjemplo, paginacion: { total: estado.vacio ? 0 : 6 } }] } },
  useConteosBitacora: () => ({ CORTE_ANUNCIADO: 2, CORTE_RESTABLECIDO: 2, CORTE_CONFIRMADO_POR_CIUDADANOS: 1, CORTE_DETECTADO_POR_INGESTA: 1 }),
  useSustento: (_id: string, abierto: boolean) => {
    estado.sustentoAbierto = abierto
    return { isPending: !abierto, isError: false, data: abierto ? { pages: [{ ids: ['reporte-1'] }] } : undefined, hasNextPage: false }
  },
}))

describe('Bitácora', () => {
  afterEach(() => { estado.error = false; estado.fallo = false; estado.vacio = false; estado.sustentoAbierto = false })

  it('debeTitularConElUltimoCambioYAgruparPorDia', () => {
    render(<Bitacora />)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('El último cambio fue hace 30 min')
    expect(screen.getByRole('heading', { name: 'Hoy' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ayer' })).toBeInTheDocument()
    expect(screen.getByText(/6 eventos/)).toBeInTheDocument()
  })

  it('debeMostrarLosTiposComoPestanasConSuCantidad', () => {
    render(<Bitacora />)
    expect(screen.getByRole('button', { name: 'Todos 6' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Anunciados 2' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: 'En boletines 1' })).toBeInTheDocument()
  })

  it('noDebeNombrarLoQueNoExiste', () => {
    render(<Bitacora />)
    expect(screen.queryByText('Sin enlace a la fuente')).not.toBeInTheDocument()
    expect(screen.queryByText('Informativo')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Boletín de Acuacar' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /Sustentado por/ })).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Sustentado por 5 reportes' })).toBeInTheDocument()
  })

  it('debeAbrirElSustentoSoloTrasLaAccion', async () => {
    render(<Bitacora />)
    expect(estado.sustentoAbierto).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Sustentado por 5 reportes' }))
    expect(estado.sustentoAbierto).toBe(true)
    expect(screen.getByText('reporte-1')).toBeInTheDocument()
  })

  it('debeSituarElErrorDeFiltroJuntoALosFiltros', () => {
    estado.error = true
    render(<Bitacora />)
    expect(screen.getByRole('alert')).toHaveTextContent('Revisa las fechas')
    expect(screen.queryByText('No pudimos consultar la bitácora')).not.toBeInTheDocument()
  })

  it('debeExplicarLaAusenciaDeEventosFiltrados', () => {
    estado.vacio = true
    render(<Bitacora />)
    expect(screen.getByText('No hay eventos con esos filtros')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Cada cambio del agua, con su fuente')
  })

  it('debeOfrecerReintentarAnteUnErrorReal', () => {
    estado.fallo = true
    render(<Bitacora />)
    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos consultar la bitácora')
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
