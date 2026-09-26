import { useEffect, useRef, useState } from 'react'
import * as maplibregl from 'maplibre-gl'
import type { GeoJSONSource } from 'maplibre-gl'
import { Protocol } from 'pmtiles'
import type { FeatureCollection, Geometry, Position } from 'geojson'
import { crearEstiloBase, leerNeutros } from './estilo-base'
import { unirSectores, type Sector } from '../dominio/sectores'
import 'maplibre-gl/dist/maplibre-gl.css'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
maplibregl.setWorkerUrl(workerUrl)
const protocolo = new Protocol()
maplibregl.addProtocol('pmtiles', protocolo.tile)
function enfocar(instancia: maplibregl.Map, geometria: FeatureCollection<Geometry>, elegido: string | undefined) {
  const figura = geometria.features.find((elemento) => String(elemento.id) === elegido)
  if (!figura || !('coordinates' in figura.geometry)) return
  const posiciones: Position[] = []
  const recorrer = (coordenadas: unknown) => {
    if (!Array.isArray(coordenadas)) return
    if (typeof coordenadas[0] === 'number') posiciones.push(coordenadas as Position)
    else coordenadas.forEach(recorrer)
  }
  recorrer(figura.geometry.coordinates)
  const limites = new maplibregl.LngLatBounds()
  posiciones.forEach((posicion) => limites.extend([posicion[0]!, posicion[1]!]))
  if (!limites.isEmpty()) instancia.fitBounds(limites, { padding: 60, maxZoom: 14, duration: matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 620 })
}
export default function Mapa({ geometria, sectores, elegido, abrir }: { geometria: FeatureCollection<Geometry>; sectores: Sector[]; elegido: string | undefined; abrir: (id: string) => void }) {
  const contenedor = useRef<HTMLDivElement>(null)
  const mapa = useRef<maplibregl.Map | null>(null)
  const datos = useRef({ geometria, sectores, elegido, abrir })
  useEffect(() => { datos.current = { geometria, sectores, elegido, abrir } }, [geometria, sectores, elegido, abrir])
  const [fallo, setFallo] = useState(false)
  useEffect(() => {
    if (!contenedor.current) return
    let instancia: maplibregl.Map
    try {
      instancia = new maplibregl.Map({ container: contenedor.current, style: crearEstiloBase(leerNeutros(), location.origin), center: [-75.49, 10.40], zoom: 11, attributionControl: false })
    } catch { queueMicrotask(() => setFallo(true)); return }
    mapa.current = instancia
    const pintar = () => {
      if (instancia.getSource('barrios')) return
      const css = getComputedStyle(document.documentElement)
      const color = (nombre: string) => css.getPropertyValue(nombre).trim()
      const trama = document.createElement('canvas'); trama.width = 8; trama.height = 8
      const contexto = trama.getContext('2d')!
      contexto.fillStyle = color('--superficie'); contexto.fillRect(0, 0, 8, 8)
      contexto.strokeStyle = color('--tinta-2'); contexto.lineWidth = 1
      contexto.beginPath(); contexto.moveTo(-4, 8); contexto.lineTo(8, -4); contexto.moveTo(0, 12); contexto.lineTo(12, 0); contexto.stroke()
      instancia.addImage('sin-datos', contexto.getImageData(0, 0, 8, 8))
      instancia.addSource('barrios', { type: 'geojson', data: unirSectores(datos.current.geometria, datos.current.sectores), promoteId: 'id' })
      instancia.addLayer({ id: 'estados', type: 'fill', source: 'barrios', filter: ['!=', ['get', 'estado'], null], paint: { 'fill-opacity': css.colorScheme === 'dark' ? .22 : .16,
        'fill-color': ['match', ['get', 'estado'], 'SIN_SERVICIO', color('--estado-sin-servicio'), 'CON_SERVICIO', color('--estado-con-servicio'), 'PRESION_BAJA', color('--estado-presion-baja'), 'CORTE_PROGRAMADO', color('--estado-corte-programado'), color('--superficie')] } })
      instancia.addLayer({ id: 'sin-datos', type: 'fill', source: 'barrios', filter: ['==', ['get', 'estado'], null], paint: { 'fill-pattern': 'sin-datos', 'fill-opacity': 0.4 } })
      instancia.addLayer({ id: 'bordes', type: 'line', source: 'barrios', paint: { 'line-color': ['match', ['get', 'estado'], 'SIN_SERVICIO', color('--estado-sin-servicio'), 'CON_SERVICIO', color('--estado-con-servicio'), 'PRESION_BAJA', color('--estado-presion-baja'), 'CORTE_PROGRAMADO', color('--estado-corte-programado'), color('--tinta-2')], 'line-width': 0.8, 'line-opacity': 0.6 } })
      instancia.addLayer({ id: 'elegido', type: 'line', source: 'barrios', filter: ['==', ['get', 'id'], datos.current.elegido ?? ''], paint: { 'line-color': color('--laton'), 'line-width': 3 } })
      enfocar(instancia, datos.current.geometria, datos.current.elegido)
      instancia.on('sourcedata', (evento) => {
        if (evento.sourceId === 'barrios' && evento.isSourceLoaded) {
          contenedor.current?.setAttribute('data-estados-listos', 'true')
        }
      })
    }
    instancia.on('style.load', pintar)
    instancia.on('click', (evento) => {
      if (!instancia.getLayer('estados')) return
      const figuras = instancia.queryRenderedFeatures(evento.point, { layers: ['estados', 'sin-datos'] })
      const id = figuras[0]?.properties?.id as string | undefined
      if (id) datos.current.abrir(id)
    })
    let sinBase = false
    instancia.on('error', (evento) => {
      if (!sinBase && String(evento.error).includes('cartagena.pmtiles')) { sinBase = true; instancia.setStyle(crearEstiloBase(leerNeutros(), location.origin, false)) }
    })
    const observador = new MutationObserver(() => instancia.setStyle(crearEstiloBase(leerNeutros(), location.origin, !sinBase)))
    observador.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    const sistema = matchMedia('(prefers-color-scheme: dark)')
    const cambiarTema = () => instancia.setStyle(crearEstiloBase(leerNeutros(), location.origin, !sinBase))
    sistema.addEventListener('change', cambiarTema)
    return () => { observador.disconnect(); sistema.removeEventListener('change', cambiarTema); instancia.remove(); mapa.current = null }
  }, [])
  useEffect(() => {
    const instancia = mapa.current
    const fuente = instancia?.getSource('barrios') as GeoJSONSource | undefined
    fuente?.setData(unirSectores(geometria, sectores))
    if (!instancia?.getLayer('elegido')) return
    instancia.setFilter('elegido', ['==', ['get', 'id'], elegido ?? ''])
    enfocar(instancia, geometria, elegido)
  }, [geometria, sectores, elegido])
  return <div className="mapa-marco"><section ref={contenedor} className="mapa" aria-label="Mapa de los barrios de Cartagena" />{fallo ? <p className="mapa-aviso">Tu navegador no pudo mostrar el mapa. Consulta todos los barrios en la lista.</p> : <div className="zoom"><button aria-label="Acercar mapa" onClick={() => mapa.current?.zoomIn({ duration: 0 })}>+</button><button aria-label="Alejar mapa" onClick={() => mapa.current?.zoomOut({ duration: 0 })}>−</button></div>}<span className="atribucion">© OpenStreetMap</span></div>
}
