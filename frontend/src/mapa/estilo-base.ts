import { layers, LIGHT } from '@protomaps/basemaps'
import type { StyleSpecification, LayerSpecification } from 'maplibre-gl'

export interface NeutrosMapa { papel: string; superficie: string; linea: string; lineaSuave: string; tinta2: string }

export function leerNeutros(): NeutrosMapa {
  const css = getComputedStyle(document.documentElement)
  const token = (nombre: string) => css.getPropertyValue(nombre).trim()
  return { papel: token('--papel'), superficie: token('--superficie'), linea: token('--linea'),
    lineaSuave: token('--linea-suave'), tinta2: token('--tinta-2') }
}

export function crearEstiloBase(neutros: NeutrosMapa, origen: string, conBase = true): StyleSpecification {
  const capas: LayerSpecification[] = layers('cartagena', LIGHT, { lang: 'es' }).map((original) => {
    const capa = structuredClone(original) as LayerSpecification
    if (capa.paint) {
      for (const clave of Object.keys(capa.paint)) {
        if (!clave.endsWith('-color')) continue
        const color = clave === 'text-color' ? neutros.tinta2
          : clave === 'text-halo-color' || clave === 'background-color' ? neutros.papel
          : capa.id.includes('water') ? neutros.lineaSuave
          : clave === 'line-color' || capa.id.includes('building') ? neutros.linea : neutros.superficie
        Object.assign(capa.paint, { [clave]: color })
      }
    }
    if (capa.type === 'symbol' && capa.layout) {
      delete capa.layout['icon-image']
      if (capa.layout['text-field']) capa.layout['text-font'] = ['Noto Sans Regular']
    }
    return capa
  })
  return {
    version: 8,
    glyphs: `${origen}/mapa/glifos/{fontstack}/{range}.pbf`,
    sources: conBase ? { cartagena: { type: 'vector', url: `pmtiles://${origen}/mapa/cartagena.pmtiles`, attribution: '© OpenStreetMap' } } : {},
    layers: conBase ? capas : [{ id: 'fondo', type: 'background', paint: { 'background-color': neutros.papel } }],
  }
}
