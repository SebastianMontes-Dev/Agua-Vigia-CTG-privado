import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { contraste } from './contraste'

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

const ESTADOS = ['estado-con-servicio', 'estado-sin-servicio', 'estado-presion-baja', 'estado-corte-programado']

// Los pares que la interfaz puede usar. Lo que no está aquí no se usa como texto: por ejemplo --tinta-terciaria
// no llega a 4,5:1 en claro ni --acento-vivo se usa como texto (guia-frontend.md §2.2).
const TEXTO: ReadonlyArray<[string, string]> = [
  ...['tinta', 'tinta-secundaria', 'acento'].flatMap((t): Array<[string, string]> =>
    ['superficie', 'fondo', 'acento-suave'].map((f) => [t, f]),
  ),
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

// Cifras de guia-frontend.md §2.3: si cambian, cambió el acento o un fondo y hay que volver a medir y documentar.
const ACENTO_MEDIDO: Record<Tema, ReadonlyArray<[string, number]>> = {
  claro: [
    ['fondo', 5.09],
    ['superficie', 5.39],
    ['acento-suave', 4.62],
  ],
  oscuro: [
    ['fondo', 8.6],
    ['superficie', 7.57],
    ['acento-suave', 5.64],
  ],
}

describe.each<Tema>(['claro', 'oscuro'])('acento en el tema %s', (tema) => {
  const paleta = colores(tema)

  it.each(ACENTO_MEDIDO[tema])('debeDarElContrasteDocumentado: --acento sobre --%s = %s:1', (fondo, esperado) => {
    expect(contraste(paleta.get('acento') ?? '', paleta.get(fondo) ?? '')).toBeCloseTo(esperado, 2)
  })
})

describe('contraste', () => {
  it('debeDarLosExtremosDeLaEscalaWcag', () => {
    expect(contraste('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contraste('#777777', '#777777')).toBe(1)
  })
})
