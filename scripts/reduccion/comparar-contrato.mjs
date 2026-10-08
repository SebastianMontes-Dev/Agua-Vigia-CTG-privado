#!/usr/bin/env node
/**
 * Guarda y compara el contrato HTTP del backend (R0 de docs/reduccion). Cada fase de la reducción debe dejarlo idéntico.
 *
 *   node scripts/reduccion/comparar-contrato.mjs guardar    # escribe scripts/reduccion/linea-base/api-docs.json
 *   node scripts/reduccion/comparar-contrato.mjs comparar   # imprime cada diferencia; sale con 1 si hay alguna
 *
 * Lee `${API_URL:-http://localhost:8081}/v3/api-docs` (springdoc, público en el perfil docker). Compara rutas, métodos,
 * operationId, tags, parámetros, cuerpos, respuestas por código, esquemas y seguridad; la redacción (description, summary,
 * example) no es contrato. Además avisa si `backend/openapi.yaml` quedó atrás respecto de lo que sirve el backend.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'
import { diferencias, normalizarContrato, operacionesDe, serializar } from './lib/forma.mjs'

const AQUI = dirname(fileURLToPath(import.meta.url))
const RAIZ = resolve(AQUI, '..', '..')
const LINEA_BASE = resolve(AQUI, 'linea-base', 'api-docs.json')
const API = (process.env.API_URL ?? 'http://localhost:8081').replace(/\/$/, '')

const modo = process.argv[2]
if (!['guardar', 'comparar'].includes(modo)) {
  console.error('Uso: node scripts/reduccion/comparar-contrato.mjs guardar|comparar')
  process.exit(2)
}

const respuesta = await fetch(`${API}/v3/api-docs`, { headers: { Accept: 'application/json' } }).catch((e) => {
  console.error(`No se pudo leer ${API}/v3/api-docs: ${e.message}. ¿Está levantado el backend?`)
  process.exit(2)
})
if (!respuesta.ok) {
  console.error(`GET ${API}/v3/api-docs respondió ${respuesta.status}`)
  process.exit(2)
}
const vivo = normalizarContrato(await respuesta.json())

// El YAML versionado no es la línea base, pero si queda atrás el frontend genera sus tipos de un contrato viejo.
let avisosYaml = []
const rutaYaml = resolve(RAIZ, 'backend', 'openapi.yaml')
if (existsSync(rutaYaml)) {
  const enYaml = new Set(operacionesDe(parse(readFileSync(rutaYaml, 'utf8'))))
  const enVivo = new Set(operacionesDe(vivo))
  avisosYaml = [
    ...[...enVivo].filter((o) => !enYaml.has(o)).map((o) => `${o}: lo sirve el backend y falta en backend/openapi.yaml`),
    ...[...enYaml].filter((o) => !enVivo.has(o)).map((o) => `${o}: está en backend/openapi.yaml y el backend ya no lo sirve`),
  ]
}

if (modo === 'guardar') {
  mkdirSync(dirname(LINEA_BASE), { recursive: true })
  writeFileSync(LINEA_BASE, serializar(vivo))
  console.log(`Contrato guardado en ${LINEA_BASE} (${operacionesDe(vivo).length} operaciones).`)
  for (const aviso of avisosYaml) console.log(`  aviso: ${aviso}`)
  process.exit(0)
}

if (!existsSync(LINEA_BASE)) {
  console.error(`No hay línea base en ${LINEA_BASE}. Corre primero: node scripts/reduccion/comparar-contrato.mjs guardar`)
  process.exit(2)
}
const diferenciasEncontradas = diferencias(JSON.parse(readFileSync(LINEA_BASE, 'utf8')), vivo)
for (const aviso of avisosYaml) console.log(`aviso: ${aviso}`)
if (diferenciasEncontradas.length) {
  console.log(`${diferenciasEncontradas.length} diferencias de contrato contra la línea base:`)
  for (const d of diferenciasEncontradas) console.log(`  ${d}`)
  process.exit(1)
}
console.log(`Contrato idéntico a la línea base (${operacionesDe(vivo).length} operaciones, 0 diferencias).`)
