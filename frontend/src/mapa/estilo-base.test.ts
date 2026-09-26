import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { crearEstiloBase } from './estilo-base'

const css = readFileSync(resolve(import.meta.dirname, '../estilos/tokens.css'), 'utf8')
describe('mapa base local', () => {
  for (const [tema, bloque] of [['claro', css.split('@media')[0]], ['oscuro', css.split(":root[data-theme='dark']")[1]]] as const) {
    it(`usa solo neutros del tema ${tema} sin colores de servicio`, () => {
      const token = (nombre: string) => new RegExp(`${nombre}:\\s*(#[0-9a-f]+)`).exec(bloque ?? '')?.[1] ?? ''
      const neutros = { papel: token('--papel'), superficie: token('--superficie'), linea: token('--linea'), lineaSuave: token('--linea-suave'), tinta2: token('--tinta-2') }
      const estilo = crearEstiloBase(neutros, 'http://localhost:5173')
      const permitidos = Object.values(neutros)
      expect(estilo.layers.length).toBeGreaterThan(10)
      for (const capa of estilo.layers) {
        for (const [, valor] of Object.entries(capa.paint ?? {}).filter(([clave]) => clave.endsWith('-color'))) expect(permitidos).toContain(valor)
      }
      const fuentes = estilo.layers.flatMap((elemento) => elemento.type === 'symbol' && elemento.layout?.['text-field'] ? [elemento.layout['text-font']] : [])
      for (const fuente of fuentes) expect(fuente).toEqual(['Noto Sans Regular'])
      expect(estilo.glyphs).toBe('http://localhost:5173/mapa/glifos/{fontstack}/{range}.pbf')
      expect(estilo.sprite).toBeUndefined()
      expect(JSON.stringify(estilo.sources)).toContain('pmtiles://http://localhost:5173/mapa/cartagena.pmtiles')
      expect(JSON.stringify(estilo.sources)).toContain('© OpenStreetMap')
    })
  }
  it('permite mostrar polígonos sobre el fondo cuando falta el extracto', () => {
    const estilo = crearEstiloBase({ papel: 'papel', superficie: '', linea: '', lineaSuave: '', tinta2: '' }, 'http://localhost', false)
    expect(estilo.sources).toEqual({}); expect(estilo.layers).toHaveLength(1)
  })
})
