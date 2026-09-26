import { useEffect } from 'react'
import type { FeatureCollection, Geometry, Position } from 'geojson'
import type { Sector } from '../dominio/sectores'
import { presentarEstado } from '../dominio/estados'
export function trazarPoligono(anillos: Position[][], proyectar: (posicion: Position) => [number, number]) {
  return anillos.map((anillo) => anillo.map((posicion, indice) => `${indice === 0 ? 'M' : 'L'}${proyectar(posicion).map((valor) => valor.toFixed(2)).join(',')}`).join(' ') + 'Z').join(' ')
}
export function RespuestaMapa({ geometria, sectores }: { geometria: FeatureCollection<Geometry>; sectores: Sector[] }) {
  useEffect(() => { requestAnimationFrame(() => { performance.mark('estados-pintados'); void import('../estilos/editorial.css') }) }, [])
  const porId = new Map(sectores.map((sector) => [sector.id, sector]))
  const mercator = (latitud: number) => Math.log(Math.tan(Math.PI / 4 + latitud * Math.PI / 360))
  const norte = mercator(10.56), sur = mercator(10.25)
  const proyectar = (posicion: Position): [number, number] => [(posicion[0]! + 75.62) / .26 * 800, (norte - mercator(posicion[1]!)) / (norte - sur) * 800]
  return <div className="respuesta-mapa" data-estados-iniciales="true" aria-hidden="true"><svg viewBox="0 0 800 800" preserveAspectRatio="xMidYMid meet"><defs><pattern id="trama-inicial" width="8" height="8" patternUnits="userSpaceOnUse"><path d="M-2 2L2-2M0 8L8 0M6 10L10 6" stroke="var(--tinta-2)" strokeWidth="1" /></pattern></defs>{geometria.features.map((figura) => {
    const estado = porId.get(String(figura.id))?.estado ?? null
    const color = presentarEstado(estado).variableColor
    const poligonos = figura.geometry.type === 'MultiPolygon' ? figura.geometry.coordinates : figura.geometry.type === 'Polygon' ? [figura.geometry.coordinates] : []
    return <path key={figura.id} d={poligonos.map((anillos) => trazarPoligono(anillos, proyectar)).join(' ')} fill={color ? `var(${color})` : 'url(#trama-inicial)'} fillRule="evenodd" fillOpacity={estado === 'SIN_SERVICIO' ? .3 : .22} stroke={color ? `var(${color})` : 'var(--tinta-2)'} strokeWidth=".8" />
  })}</svg><p className="mapa-aviso">Estados publicados. Preparando el mapa interactivo…</p></div>
}
