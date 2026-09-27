import { Outlet, useNavigate, useParams } from '@tanstack/react-router'
import { lazy, Suspense, useCallback } from 'react'
import { useGeometria, useListado } from '../../app/datos'
import { Buscador } from '../../componentes/Buscador'
import estilos from './PantallaMapa.module.css'

// MapLibre pesa más que todo lo demás junto: se carga aparte para que la respuesta del panel no lo espere (DESIGN.md §8).
const Mapa = lazy(() => import('../../mapa/Mapa'))

function AreaMapa({ seleccionado, alElegir }: { seleccionado: string | null; alElegir: (id: string) => void }) {
  const geometria = useGeometria()
  const { porId, lectura } = useListado()

  if (geometria.isError) {
    return (
      <div className={estilos.aviso} role="note">
        <p>No pudimos cargar el dibujo de los barrios. Búscalo por su nombre o ábrelo desde la lista.</p>
        <button type="button" className={estilos.reintentar} onClick={() => geometria.refetch()}>Reintentar</button>
      </div>
    )
  }
  if (!geometria.data || !lectura.listado) {
    return <div className={estilos.cargando} aria-hidden="true" />
  }
  return (
    <Suspense fallback={<div className={estilos.cargando} aria-hidden="true" />}>
      <Mapa geometria={geometria.data} sectores={porId} seleccionado={seleccionado} alElegir={alElegir} />
    </Suspense>
  )
}

/** El mapa queda montado mientras se cambia entre la ciudad y un barrio: el panel cambia, la cámara viaja. */
export function PantallaMapa() {
  const { id } = useParams({ strict: false })
  const navegar = useNavigate()
  const { sectores, lectura } = useListado()
  const elegir = useCallback((sectorId: string) => {
    void navegar({ to: '/sectores/$id', params: { id: sectorId } })
  }, [navegar])

  return (
    <div className={estilos.pantalla}>
      <search className={estilos.buscador}>
        <Buscador sectores={sectores} cargando={!lectura.listado} alElegir={elegir} />
      </search>
      <div className={estilos.mapa}>
        <AreaMapa seleccionado={id ?? null} alElegir={elegir} />
      </div>
      <div className={estilos.panel}>
        <Outlet />
      </div>
    </div>
  )
}
