import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const carpeta = import.meta.dirname
const iconos = readdirSync(carpeta).filter((archivo) => archivo.endsWith('.svg'))

describe('iconos SVG', () => {
  it('debeHaberUnGlifoPorEstadoMasElDeSinDatos', () => {
    expect(iconos.filter((archivo) => archivo.startsWith('estado-')).sort()).toEqual([
      'estado-con-servicio.svg',
      'estado-corte-programado.svg',
      'estado-presion-baja.svg',
      'estado-sin-datos.svg',
      'estado-sin-servicio.svg',
    ])
  })

  // El color sale del token del estado (DESIGN.md §2): un color fijo en el SVG haría que el glifo y el mapa divergieran.
  it.each(iconos)('noDebeFijarColores: %s', (archivo) => {
    const svg = readFileSync(resolve(carpeta, archivo), 'utf8')
    const colores = [...svg.matchAll(/(?:fill|stroke)="([^"]+)"/g)].map((m) => m[1])
    expect(colores.filter((c) => !['currentColor', 'none', '#fff', '#000'].includes(c ?? ''))).toEqual([])
    expect(svg).toContain('aria-hidden="true"')
  })
})
