import { useInfiniteQuery } from '@tanstack/react-query'
import { api, normalizarError } from '../api/cliente'
import { leerPaginacion } from '../api/paginacion'
import type { ReportePendiente } from '../api/moderacion'

const TAMANO_COLA = 20

/** La cola de moderación: los más antiguos primero, por lotes (docs/api/panel-veedor.md). */
export function useReportesPendientes() {
  return useInfiniteQuery({
    queryKey: ['panel', 'reportes-pendientes'],
    initialPageParam: 0,
    queryFn: async ({ pageParam, signal }) => {
      const { data, error, response } = await api.GET('/api/veedor/reportes/pendientes', {
        params: { query: { pagina: pageParam, tamano: TAMANO_COLA } }, signal,
      }).catch(() => ({ data: undefined, error: undefined, response: null }))
      if (!response || !response.ok || !data) throw normalizarError(response ?? null, error)
      return { reportes: data as ReportePendiente[], paginacion: leerPaginacion(response.headers, pageParam) }
    },
    getNextPageParam: (ultima) => (ultima.paginacion.hayMas ? ultima.paginacion.pagina + 1 : undefined),
    staleTime: 5_000,
  })
}
