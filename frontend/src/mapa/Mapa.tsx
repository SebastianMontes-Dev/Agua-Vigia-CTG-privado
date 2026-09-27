import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { addProtocol, setWorkerUrl, Map as MapaMapLibre, type GeoJSONSource, type LngLatBoundsLike, type StyleSpecification } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { Protocol } from 'pmtiles'
// MapLibre 6 busca su worker junto a su propio módulo, que Vite reempaqueta: se empaqueta aparte y se le indica dónde está.
import urlWorker from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import type { GeometriaSectores } from '../api/geometria'
import type { Sector } from '../dominio/sectores'
import { leerEsquema, suscribirEsquema } from '../app/tema'
import { crearEstiloBase, leerNeutros } from './estilo-base'
import {
  CAPA_RELLENO, CAPA_TRAMA, FUENTE_SECTORES, IMAGEN_TRAMA, capasSectores, dibujarTrama, leerColoresMapa, unirSectores,
} from './capas-sectores'
import { Leyenda } from './Leyenda'
import estilos from './Mapa.module.css'

// Casco urbano; los corregimientos e islas aparecen al alejar. Los límites dejan fuera lo que no es Cartagena.
const VISTA_CIUDAD: LngLatBoundsLike = [[-75.565, 10.355], [-75.435, 10.46]]
const LIMITES: LngLatBoundsLike = [[-76.4, 9.2], [-75.1, 10.9]]
const DURACION_ELEGIR_MS = 620
type PropiedadPintura = Parameters<MapaMapLibre['setPaintProperty']>[1]

let protocoloRegistrado = false
function registrarPmtiles() {
  if (protocoloRegistrado) return
  setWorkerUrl(urlWorker)
  addProtocol('pmtiles', new Protocol().tile)
  protocoloRegistrado = true
}

// cubic-bezier(.65, 0, .35, 1) de --lento, aproximada por una cúbica simétrica.
function lento(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2
}

function movimientoReducido(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

function limitesDe(geometria: GeometriaSectores, id: string): LngLatBoundsLike | null {
  const feature = geometria.features.find((f) => String(f.id) === id)
  if (!feature) return null
  let [oeste, sur, este, norte] = [180, 90, -180, -90]
  const recorrer = (coordenadas: unknown): void => {
    if (Array.isArray(coordenadas) && typeof coordenadas[0] === 'number') {
      const [lon, lat] = coordenadas as number[]
      oeste = Math.min(oeste, lon!); este = Math.max(este, lon!); sur = Math.min(sur, lat!); norte = Math.max(norte, lat!)
    } else if (Array.isArray(coordenadas)) coordenadas.forEach(recorrer)
  }
  recorrer(feature.geometry.coordinates)
  return [[oeste, sur], [este, norte]]
}

interface Props {
  geometria: GeometriaSectores
  sectores: ReadonlyMap<string, Sector>
  seleccionado: string | null
  alElegir: (id: string) => void
}

export default function Mapa({ geometria, sectores, seleccionado, alElegir }: Props) {
  const contenedor = useRef<HTMLElement>(null)
  const mapa = useRef<MapaMapLibre | null>(null)
  const [sinWebGl, setSinWebGl] = useState(false)
  const esquema = useSyncExternalStore(suscribirEsquema, leerEsquema)
  const datos = useMemo(() => unirSectores(geometria, sectores), [geometria, sectores])
  const ultimo = useRef({ datos, seleccionado, alElegir })
  ultimo.current = { datos, seleccionado, alElegir }

  function estiloCompleto(): StyleSpecification {
    const colores = leerColoresMapa()
    const base = crearEstiloBase(leerNeutros(), window.location.origin)
    return {
      ...base,
      sources: { ...base.sources, [FUENTE_SECTORES]: { type: 'geojson', data: ultimo.current.datos } },
      layers: [...base.layers, ...capasSectores(colores, ultimo.current.seleccionado)],
    }
  }

  useEffect(() => {
    if (!contenedor.current) return
    registrarPmtiles()
    let instancia: MapaMapLibre
    try {
      instancia = new MapaMapLibre({
        container: contenedor.current,
        style: estiloCompleto(),
        bounds: VISTA_CIUDAD,
        maxBounds: LIMITES,
        minZoom: 8,
        maxZoom: 17,
        attributionControl: false,
        dragRotate: false,
        pitchWithRotate: false,
        touchPitch: false,
        // En el celular el mapa va dentro de la página: un dedo desplaza la página y dos mueven el mapa.
        cooperativeGestures: window.matchMedia?.('(pointer: coarse)').matches ?? false,
        locale: { 'CooperativeGesturesHandler.MobileHelpText': 'Usa dos dedos para mover el mapa' },
      })
    } catch {
      setSinWebGl(true)
      return
    }
    instancia.touchZoomRotate.disableRotation()
    instancia.setMissingStyleImageResolver((id) => {
      if (id !== IMAGEN_TRAMA || instancia.hasImage(IMAGEN_TRAMA)) return
      const trama = dibujarTrama(leerColoresMapa())
      if (trama) instancia.addImage(IMAGEN_TRAMA, trama, { pixelRatio: 2 })
    })
    for (const capa of [CAPA_RELLENO, CAPA_TRAMA]) {
      instancia.on('click', capa, (evento) => {
        const id = evento.features?.[0]?.properties?.id
        if (typeof id === 'string') ultimo.current.alElegir(id)
      })
      instancia.on('mouseenter', capa, () => { instancia.getCanvas().style.cursor = 'pointer' })
      instancia.on('mouseleave', capa, () => { instancia.getCanvas().style.cursor = '' })
    }
    mapa.current = instancia
    return () => { instancia.remove(); mapa.current = null }
    // El mapa se crea una vez; datos, tema y selección se aplican en sus propios efectos.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const esquemaAplicado = useRef(esquema)
  useEffect(() => {
    const instancia = mapa.current
    if (!instancia || esquemaAplicado.current === esquema) return
    esquemaAplicado.current = esquema
    // La trama lleva los colores del tema: se quita para que el resolutor la dibuje de nuevo.
    if (instancia.hasImage(IMAGEN_TRAMA)) instancia.removeImage(IMAGEN_TRAMA)
    instancia.setStyle(estiloCompleto(), { diff: false })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [esquema])

  useEffect(() => {
    const fuente = mapa.current?.getSource(FUENTE_SECTORES) as GeoJSONSource | undefined
    fuente?.setData(datos)
  }, [datos])

  useEffect(() => {
    const instancia = mapa.current
    if (!instancia) return
    const aplicar = () => {
      for (const capa of capasSectores(leerColoresMapa(), seleccionado)) {
        if (!instancia.getLayer(capa.id)) continue
        instancia.setFilter(capa.id, 'filter' in capa ? capa.filter ?? null : null)
        for (const [propiedad, valor] of Object.entries(capa.paint ?? {})) {
          instancia.setPaintProperty(capa.id, propiedad as PropiedadPintura, valor)
        }
      }
      const destino = seleccionado ? limitesDe(geometria, seleccionado) : VISTA_CIUDAD
      if (destino) {
        const estrecho = instancia.getContainer().clientWidth < 600
        instancia.fitBounds(destino, {
          padding: estrecho ? 16 : 48, maxZoom: 15, easing: lento, duration: movimientoReducido() ? 0 : DURACION_ELEGIR_MS,
        })
      }
    }
    if (instancia.isStyleLoaded()) aplicar()
    else instancia.once('idle', aplicar)
  }, [seleccionado, geometria])

  if (sinWebGl) {
    return (
      <div className={estilos.sinMapa} role="note">
        <p>Este navegador no puede dibujar el mapa. Busca tu barrio o ábrelo desde la lista: la información es la misma.</p>
      </div>
    )
  }

  return (
    <div className={estilos.marco}>
      <section ref={contenedor} className={estilos.lienzo} aria-label="Mapa de barrios por estado del agua" />
      <Leyenda />
      <div className={estilos.zoom}>
        <button type="button" aria-label="Acercar" onClick={() => mapa.current?.zoomIn()}>+</button>
        <button type="button" aria-label="Alejar" onClick={() => mapa.current?.zoomOut()}>−</button>
      </div>
      <p className={estilos.credito}>© OpenStreetMap · Barrios: Cartagena Cómo Vamos</p>
    </div>
  )
}
