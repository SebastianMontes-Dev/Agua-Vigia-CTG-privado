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
  return new Map([...bloque.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-f]{6});/g)].map((m) => [m[1] ?? '', m[2] ?? '']))
}

const ESTADOS = ['estado-con-servicio', 'estado-sin-servicio', 'estado-presion-baja', 'estado-corte-programado']
const FONDOS = ['papel', 'superficie', 'elevada']

// Los pares que la interfaz puede usar como texto (identidad.md §2). --tinta-3 nunca es texto, y el texto de un
// estado va en --tinta junto a su glifo: el color del estado no se usa como texto.
const TEXTO: ReadonlyArray<[string, string]> = ['tinta', 'tinta-2', 'cardenillo', 'laton'].flatMap(
  (t): Array<[string, string]> => [...FONDOS, 'cardenillo-suave'].map((f) => [t, f]),
)

// WCAG 1.4.11: bordes funcionales, foco en latón y glifos de estado necesitan 3:1 contra el fondo.
const NO_TEXTO: ReadonlyArray<[string, string]> = ['tinta-3', 'cardenillo', 'laton', ...ESTADOS].flatMap(
  (c): Array<[string, string]> => FONDOS.map((f) => [c, f]),
)

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

  it('debeLlegarAlAAElTextoDelBotonPrincipal', () => {
    expect(medir('sobre-cardenillo', 'cardenillo')).toBeGreaterThanOrEqual(4.5)
  })

  it.each(NO_TEXTO)('debeLlegarATresAUnoFueraDelTexto: --%s sobre --%s', (color, fondo) => {
    expect(medir(color, fondo)).toBeGreaterThanOrEqual(3)
  })
})

// Cifras de identidad.md §2 (sobre papel): si cambian, cambió un token y hay que volver a medir y documentar.
const MEDIDO: Record<Tema, ReadonlyArray<[string, string, number]>> = {
  claro: [
    ['tinta', 'papel', 16.19],
    ['tinta-2', 'papel', 7.38],
    ['tinta-3', 'papel', 3.52],
    ['cardenillo', 'papel', 6.64],
    ['sobre-cardenillo', 'cardenillo', 7.25],
    ['laton', 'papel', 5.02],
    ['estado-con-servicio', 'papel', 4.56],
    ['estado-sin-servicio', 'papel', 5.79],
    ['estado-presion-baja', 'papel', 4.7],
    ['estado-corte-programado', 'papel', 5.93],
  ],
  oscuro: [
    ['tinta', 'papel', 16.1],
    ['tinta-2', 'papel', 8.55],
    ['tinta-3', 'papel', 4.05],
    ['cardenillo', 'papel', 9.39],
    ['sobre-cardenillo', 'cardenillo', 9.39],
    ['laton', 'papel', 8.11],
  ],
}

describe.each<Tema>(['claro', 'oscuro'])('cifras documentadas en el tema %s', (tema) => {
  const paleta = colores(tema)

  it.each(MEDIDO[tema])('debeDarElContrasteDocumentado: --%s sobre --%s = %s:1', (color, fondo, esperado) => {
    expect(contraste(paleta.get(color) ?? '', paleta.get(fondo) ?? '')).toBeCloseTo(esperado, 2)
  })
})

describe('contraste', () => {
  it('debeDarLosExtremosDeLaEscalaWcag', () => {
    expect(contraste('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contraste('#777777', '#777777')).toBe(1)
  })

  it('debeAceptarElHexadecimalCortoQueDevuelveElNavegador', () => {
    expect(contraste('#fff', '#2f5f57')).toBeCloseTo(contraste('#ffffff', '#2f5f57'), 10)
    expect(Number.isNaN(contraste('#fff', '#000'))).toBe(false)
  })
})
