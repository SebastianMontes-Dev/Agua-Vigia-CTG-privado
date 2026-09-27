import { useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { Button } from 'react-aria-components'
import { useGeometria } from '../app/datos'
import { hayGeolocalizacion, pedirUbicacion, recordarUbicacion, type ResultadoUbicacion } from '../app/ubicacion'
import { sectorEnCoordenada } from '../dominio/ubicacion'
import botones from './Botones.module.css'
import estilos from './UsarUbicacion.module.css'

type Fallo = Exclude<ResultadoUbicacion, { tipo: 'ubicada' }> | { tipo: 'fuera' } | { tipo: 'sin-geometria' }

const metros = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 })

function mensaje(fallo: Fallo): string {
  switch (fallo.tipo) {
    case 'denegada': return 'No tenemos permiso para usar tu ubicación. Puedes darlo en los ajustes del navegador o buscar tu barrio por su nombre.'
    case 'agotada': return 'Tu teléfono tardó demasiado en encontrar tu ubicación. Inténtalo otra vez o busca tu barrio por su nombre.'
    case 'no-disponible': return 'No pudimos saber dónde estás. Busca tu barrio por su nombre.'
    case 'imprecisa': return `Tu ubicación tiene un margen de unos ${metros.format(fallo.metros)} metros: no alcanza para saber en qué barrio estás. Búscalo por su nombre.`
    case 'fuera': return 'Tu ubicación no queda dentro de ninguno de los barrios del mapa. Busca el tuyo por su nombre.'
    case 'sin-geometria': return 'No pudimos cargar el dibujo de los barrios para ubicarte. Busca el tuyo por su nombre.'
  }
}

function irAlBuscador() {
  document.querySelector<HTMLInputElement>('search input')?.focus()
}

/** Guía §4.1: el permiso se pide solo al tocar; si falla, la búsqueda sigue ahí y nada queda bloqueado. */
export function UsarUbicacion() {
  const geometria = useGeometria()
  const navegar = useNavigate()
  const [buscando, setBuscando] = useState(false)
  const [fallo, setFallo] = useState<Fallo | null>(null)

  if (!hayGeolocalizacion()) return null

  async function ubicar() {
    setBuscando(true)
    setFallo(null)
    const resultado = await pedirUbicacion()
    if (resultado.tipo !== 'ubicada') {
      setBuscando(false)
      setFallo(resultado)
      return
    }
    const dibujo = geometria.data ?? (await geometria.refetch()).data
    const sectorId = dibujo ? sectorEnCoordenada(dibujo, resultado.coordenada) : null
    setBuscando(false)
    if (!dibujo || !sectorId) {
      setFallo({ tipo: dibujo ? 'fuera' : 'sin-geometria' })
      return
    }
    recordarUbicacion(sectorId, resultado.coordenada)
    void navegar({ to: '/sectores/$id', params: { id: sectorId } })
  }

  return (
    <div className={estilos.ubicacion}>
      <Button className={botones.secundario} isDisabled={buscando} onPress={() => void ubicar()}>
        {buscando ? 'Buscando tu ubicación…' : 'Usar mi ubicación'}
      </Button>
      {fallo && (
        <div className={estilos.fallo} role="alert">
          <p>{mensaje(fallo)}</p>
          <button type="button" className={estilos.enlace} onClick={irAlBuscador}>Buscar mi barrio por su nombre</button>
        </div>
      )}
    </div>
  )
}
