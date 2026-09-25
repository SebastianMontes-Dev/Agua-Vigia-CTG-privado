import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const tokens = readFileSync(resolve(import.meta.dirname, 'tokens.css'), 'utf8')

type Tema = 'claro' | 'oscuro'

const BLOQUES: Record<Tema, string> = {
  claro: ":root[data-theme='light'] {",
  oscuro: ":root[data-theme='dark'] {",
}

function colores(tema: Tema): Map<string, string> {
  const inicio = tokens.indexOf(BLOQUES[tema])
  const bloque = tokens.slice(inicio, tokens.indexOf('}', inicio))
  return new Map([...bloque.matchAll(/--([a-z-]+):\s*(#[0-9a-f]{6});/g)].map((m) => [m[1] ?? '', m[2] ?? '']))
}

function luminancia(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const canal = parseInt(hex.slice(i, i + 2), 16) / 255
    return canal <= 0.04045 ? canal / 12.92 : ((canal + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0)
}

function contraste(a: string, b: string): number {
  const [claro, oscuro] = [luminancia(a), luminancia(b)].sort((x, y) => y - x)
  return ((claro ?? 0) + 0.05) / ((oscuro ?? 0) + 0.05)
}

const ESTADOS = ['estado-con-servicio', 'estado-sin-servicio', 'estado-presion-baja', 'estado-corte-programado']

// Los pares que la interfaz puede usar. Lo que no está aquí no se usa como texto: por ejemplo --acento sobre
// --fondo en claro da 4,39:1, y --tinta-terciaria no llega a 4,5:1 en claro (plan-frontend.md §5.3).
const TEXTO: ReadonlyArray<[string, string]> = [
  ...['tinta', 'tinta-secundaria'].flatMap((t): Array<[string, string]> =>
    ['superficie', 'fondo', 'acento-suave'].map((f) => [t, f]),
  ),
  ['acento', 'superficie'],
  ...ESTADOS.flatMap((e): Array<[string, string]> => [
    [e, 'superficie'],
    [e, 'fondo'],
  ]),
]

// Texto sobre un relleno de estado o de acento (etiqueta de la tarjeta, botón principal): el color del texto
// cambia de tema, porque --tinta no llega a 3:1 sobre ninguno de los cinco rellenos.
const TEXTO_SOBRE_RELLENO: Record<Tema, string> = { claro: 'superficie', oscuro: 'fondo' }

// WCAG 1.4.11: bordes de controles, foco y glifos de estado necesitan 3:1 contra lo que tienen al lado.
const NO_TEXTO: ReadonlyArray<[string, string]> = [
  ...['acento', 'acento-vivo', 'tinta-terciaria', ...ESTADOS].flatMap((c): Array<[string, string]> => [
    [c, 'superficie'],
    [c, 'fondo'],
  ]),
]

describe.each<Tema>(['claro', 'oscuro'])('contraste en el tema %s', (tema) => {
  const paleta = colores(tema)
  const medir = (primero: string, fondo: string) => {
    const a = paleta.get(primero)
    const b = paleta.get(fondo)
    if (!a || !b) throw new Error(`tokens.css (${tema}) no declara --${primero} o --${fondo}`)
    return contraste(a, b)
  }

  it.each(TEXTO)('debeLlegarAlAAEnTextoNormal: --%s sobre --%s', (texto, fondo) => {
    expect(medir(texto, fondo)).toBeGreaterThanOrEqual(4.5)
  })

  it.each(['acento', ...ESTADOS])('debeLlegarAlAAElTextoSobreElRellenoDe --%s', (relleno) => {
    expect(medir(TEXTO_SOBRE_RELLENO[tema], relleno)).toBeGreaterThanOrEqual(4.5)
  })

  it.each(NO_TEXTO)('debeLlegarATresAUnoFueraDelTexto: --%s sobre --%s', (color, fondo) => {
    expect(medir(color, fondo)).toBeGreaterThanOrEqual(3)
  })
})

describe('contraste', () => {
  it('debeDarLosExtremosDeLaEscalaWcag', () => {
    expect(contraste('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contraste('#777777', '#777777')).toBe(1)
  })
})
