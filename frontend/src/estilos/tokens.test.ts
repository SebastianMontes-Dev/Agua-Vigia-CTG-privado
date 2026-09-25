import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const raizRepo = resolve(import.meta.dirname, '../../..')
const textoDiseno = readFileSync(resolve(raizRepo, 'DESIGN.md'), 'utf8')
const tokens = readFileSync(resolve(import.meta.dirname, 'tokens.css'), 'utf8')

const ESTADOS: Record<string, string> = {
  'Con servicio': '--estado-con-servicio',
  'Sin servicio': '--estado-sin-servicio',
  'Presión baja': '--estado-presion-baja',
  'Corte programado': '--estado-corte-programado',
}

const BASE: Record<string, string> = {
  'Acento turquesa': '--acento',
  'Acento vivo': '--acento-vivo',
  'Acento suave': '--acento-suave',
  Tinta: '--tinta',
  'Tinta secundaria': '--tinta-secundaria',
  'Tinta terciaria': '--tinta-terciaria',
  Línea: '--linea',
  Superficie: '--superficie',
  Fondo: '--fondo',
}

function valoresDeDiseno(): Map<string, { claro: string; oscuro: string }> {
  const valores = new Map<string, { claro: string; oscuro: string }>()
  for (const [nombre, variable] of Object.entries(ESTADOS)) {
    const fila = new RegExp(`\\*\\*${nombre}\\*\\* \\| \`(#[0-9a-f]{6})\` \\| \`(#[0-9a-f]{6})\``).exec(textoDiseno)
    if (!fila?.[1] || !fila[2]) throw new Error(`DESIGN.md §2 no trae la fila de «${nombre}»`)
    valores.set(variable, { claro: fila[1], oscuro: fila[2] })
  }
  for (const [nombre, variable] of Object.entries(BASE)) {
    const fila = new RegExp(`^${nombre}\\s+(#[0-9a-f]{6})\\s+(?:\\(claro\\)\\s+)?(#[0-9a-f]{6})`, 'm').exec(textoDiseno)
    if (!fila?.[1] || !fila[2]) throw new Error(`DESIGN.md §3 no trae la fila de «${nombre}»`)
    valores.set(variable, { claro: fila[1], oscuro: fila[2] })
  }
  return valores
}

function bloque(selector: string): string {
  const inicio = tokens.indexOf(selector)
  if (inicio < 0) throw new Error(`tokens.css no tiene el bloque ${selector}`)
  return tokens.slice(inicio, tokens.indexOf('}', inicio))
}

function valorEn(texto: string, variable: string): string | undefined {
  return new RegExp(`${variable}:\\s*(#[0-9a-f]{6});`).exec(texto)?.[1]
}

describe('tokens.css', () => {
  const diseno = valoresDeDiseno()
  const claro = bloque(":root[data-theme='light'] {")
  const oscuroSistema = bloque(":root:not([data-theme='light']) {")
  const oscuroInterruptor = bloque(":root[data-theme='dark'] {")

  it('debeCopiarLosTreceColoresDeDisenoEnElTemaClaro', () => {
    for (const [variable, { claro: esperado }] of diseno) {
      expect(valorEn(claro, variable), variable).toBe(esperado)
    }
  })

  it('debeCopiarLosColoresDeDisenoEnLosDosBloquesOscuros', () => {
    for (const [variable, { oscuro: esperado }] of diseno) {
      expect(valorEn(oscuroSistema, variable), `${variable} (sistema)`).toBe(esperado)
      expect(valorEn(oscuroInterruptor, variable), `${variable} (interruptor)`).toBe(esperado)
    }
  })

  it('noDebeDeclararColoresFueraDeLosTokens', () => {
    const declarados = new Set([...tokens.matchAll(/(--[a-z-]+):\s*#/g)].map((m) => m[1]))
    expect([...declarados].sort()).toEqual([...diseno.keys()].sort())
  })
})
