import { QueryClient, useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useSyncExternalStore } from 'react'
import { api, normalizarError } from '../api/cliente'
import { crearCanal, entornoNavegador, type LecturaCanal } from '../api/canal-en-vivo'
import { almacenIndexedDb, cargarGeometria } from '../api/geometria'
import { leerPaginacion } from '../api/paginacion'
import type { Corte } from '../dominio/cortes'
import type { Sector } from '../dominio/sectores'
import type { Indice, PuntoSerie } from '../dominio/cumplimiento'

// Solo GET: TanStack Query no reintenta mutaciones por defecto y aquí no se cambia (plan §6.1).
export const clienteConsultas = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (fallos, error) => fallos < 3 && (error as { reintentable?: boolean }).reintentable !== false,
      retryDelay: (intento) => Math.min(30_000, 1000 * 2 ** intento * (0.5 + Math.random())),
      refetchOnWindowFocus: false,
    },
  },
})

let canal: ReturnType<typeof crearCanal> | null = null
let usuarios = 0
function obtenerCanal() {
  canal ??= crearCanal(entornoNavegador())
  return canal
}

// Varias piezas leen el listado a la vez; el canal se abre con la primera y se cierra con la última.
function usarCanal(instancia: ReturnType<typeof crearCanal>): () => void {
  if (usuarios++ === 0) instancia.iniciar()
  return () => { if (--usuarios === 0) instancia.detener() }
}

export interface Listado {
  lectura: LecturaCanal
  porId: ReadonlyMap<string, Sector>
  sectores: readonly Sector[]
  reintentar: () => void
}

/** El canal en vivo es el dueño del listado: lo pide al empezar, al recibir un aviso y al volver a la pestaña. */
export function useListado(): Listado {
  const instancia = obtenerCanal()
  const lectura = useSyncExternalStore(instancia.suscribir, instancia.lectura)
  useEffect(() => usarCanal(instancia), [instancia])
  const sectores = useMemo(() => lectura.listado?.sectores ?? [], [lectura.listado])
  const porId = useMemo(() => new Map(sectores.map((sector) => [sector.id ?? '', sector])), [sectores])
  return { lectura, porId, sectores, reintentar: instancia.actualizar }
}

export function useGeometria() {
  return useQuery({
    queryKey: ['geometria'],
    queryFn: () => cargarGeometria(almacenIndexedDb()),
    staleTime: Infinity,
    gcTime: Infinity,
  })
}

const TAMANO_CORTES = 10

/** Se pide al abrir la ficha, nunca en segundo plano (docs/api/sectores-y-tiempo-real.md). */
export function useCortes(id: string) {
  return useInfiniteQuery({
    queryKey: ['cortes', id],
    initialPageParam: 0,
    queryFn: async ({ pageParam, signal }) => {
      const { data, error, response } = await api.GET('/api/sectores/{sectorId}/cortes', {
        params: { path: { sectorId: id }, query: { pagina: pageParam, tamano: TAMANO_CORTES } },
        signal,
      }).catch(() => ({ data: undefined, error: undefined, response: null }))
      if (!response || !response.ok || !data) throw normalizarError(response ?? null, error)
      return { cortes: data as Corte[], paginacion: leerPaginacion(response.headers, pageParam) }
    },
    getNextPageParam: (ultima) => (ultima.paginacion.hayMas ? ultima.paginacion.pagina + 1 : undefined),
    staleTime: 60_000,
  })
}

export function useIndiceCumplimiento(sectorId?: string) {
  return useQuery({
    queryKey: ['cumplimiento', sectorId ?? 'global'],
    queryFn: async ({ signal }) => {
      const resultado = sectorId
        ? await api.GET('/api/cumplimiento/sectores/{sectorId}', { params: { path: { sectorId } }, signal })
          .catch(() => ({ data: undefined, error: undefined, response: null }))
        : await api.GET('/api/cumplimiento', { signal })
          .catch(() => ({ data: undefined, error: undefined, response: null }))
      const { data, error, response } = resultado
      if (!response || !response.ok || !data) throw normalizarError(response ?? null, error)
      return data as Indice
    },
    staleTime: 5_000,
  })
}

export interface FiltrosSerie {
  sectorId?: string
  desde?: string
  hasta?: string
}

export function useSerieCumplimiento(filtros: FiltrosSerie, habilitado = true) {
  return useQuery({
    queryKey: ['cumplimiento-serie', filtros.sectorId, filtros.desde, filtros.hasta],
    enabled: habilitado,
    queryFn: async ({ signal }) => {
      const { data, error, response } = await api.GET('/api/cumplimiento/serie', {
        params: { query: { sectorId: filtros.sectorId, desde: filtros.desde, hasta: filtros.hasta } }, signal,
      }).catch(() => ({ data: undefined, error: undefined, response: null }))
      if (!response || !response.ok || !data) throw normalizarError(response ?? null, error)
      return data as PuntoSerie[]
    },
    staleTime: 5_000,
  })
}
