#!/usr/bin/env node
/**
 * Guarda y compara la ESTRUCTURA de las bases (R0 de docs/reduccion, requisito 6: MongoDB y Redis intactos). El contrato HTTP no
 * ve la base; esta herramienta la lee directamente. Solo lee: no escribe nada en Mongo ni en Redis.
 *
 *   node scripts/reduccion/esquema-datos.mjs guardar    # escribe scripts/reduccion/linea-base/esquema-datos.json
 *   node scripts/reduccion/esquema-datos.mjs comparar   # imprime cada diferencia; sale con 1 si hay alguna
 *
 * Mongo, por colección: los índices con todas sus opciones (únicos, dispersos, TTL, parciales, 2dsphere), la forma de los
 * documentos con los tipos BSON distinguidos (todos los documentos unidos: un campo que unos traen y otros no sale como opcional)
 * y los valores distintos de `_class` (Spring Data guarda ahí el nombre completo de la clase Java: si una clase cambia de paquete,
 * cambia el valor en los documentos nuevos aunque el esquema siga igual).
 * Redis, sobre la base 0: las claves agrupadas por patrón con su tipo y si caducan, y los canales de pub/sub.
 *
 * Para que Redis tenga claves de todo tipo, correr antes `node scripts/verificar-flujos.mjs`.
 *
 * Variables: MONGODB_URI (mongodb://localhost:27017/?directConnection=true), MONGODB_DB (aguavigia), REDIS_HOST (localhost),
 * REDIS_PORT (6379), REDIS_DB (0).
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { MongoClient } from 'mongodb'
import { esBaseLocal } from '../lib/base-local.mjs'
import { diferencias, formaBson, serializar, unir } from './lib/forma.mjs'
import { compararPatrones, conectarRedis, patronDeClave, resumirClaves } from './lib/redis.mjs'

const AQUI = dirname(fileURLToPath(import.meta.url))
const LINEA_BASE = resolve(AQUI, 'linea-base', 'esquema-datos.json')
const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true'
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia'
const REDIS = { host: process.env.REDIS_HOST ?? 'localhost', port: Number(process.env.REDIS_PORT ?? 6379), db: Number(process.env.REDIS_DB ?? 0) }

const modo = process.argv[2]
if (!['guardar', 'comparar'].includes(modo)) {
  console.error('Uso: node scripts/reduccion/esquema-datos.mjs guardar|comparar')
  process.exit(2)
}
if (!esBaseLocal(MONGODB_URI, { permitirServicioMongo: false })) {
  console.error('Me niego a leer una base que no es local.')
  process.exit(2)
}

async function esquemaDeMongo(db) {
  const nombres = (await db.listCollections({}, { nameOnly: true }).toArray()).map((c) => c.name).filter((n) => !n.startsWith('system.')).sort()
  const colecciones = {}
  for (const nombre of nombres) {
    const coleccion = db.collection(nombre)
    const indices = (await coleccion.indexes()).map(({ v: _v, ns: _ns, ...resto }) => resto).sort((a, b) => a.name.localeCompare(b.name))
    let forma
    const clases = new Set()
    let documentos = 0
    for await (const documento of coleccion.find({}, { promoteValues: false })) {
      documentos++
      forma = unir(forma, formaBson(documento))
      const clase = documento._class
      if (typeof clase === 'string') clases.add(clase)
    }
    colecciones[nombre] = { indices, clases: [...clases].sort(), vacia: documentos === 0, forma: forma ?? null }
  }
  return colecciones
}

async function esquemaDeRedis(idsDeSector) {
  const redis = await conectarRedis(REDIS)
  try {
    const claves = []
    let cursor = '0'
    do {
      const [siguiente, lote] = await redis.comando('SCAN', cursor, 'COUNT', 1000)
      cursor = siguiente
      for (const clave of lote) {
        claves.push({ patron: patronDeClave(clave, idsDeSector), tipo: await redis.comando('TYPE', clave), ttl: await redis.comando('TTL', clave) })
      }
    } while (cursor !== '0')
    const canales = (await redis.comando('PUBSUB', 'CHANNELS')).sort()
    return { patrones: resumirClaves(claves.filter((c) => c.ttl !== -2)), canales }
  } finally {
    redis.cerrar()
  }
}

const cliente = new MongoClient(MONGODB_URI, { serverSelectionTimeoutMS: 5000 })
let vivo
try {
  await cliente.connect()
  const db = cliente.db(DB_NAME)
  const sectores = await db.collection('sectores').find({}, { projection: { _id: 1, slug: 1, id: 1 } }).toArray()
  const idsDeSector = new Set(sectores.flatMap((s) => [s._id, s.slug, s.id]).filter((v) => typeof v === 'string'))
  vivo = { mongo: await esquemaDeMongo(db), redis: await esquemaDeRedis(idsDeSector) }
} catch (e) {
  console.error(`No se pudo leer el esquema: ${e.message}`)
  process.exit(2)
} finally {
  await cliente.close()
}

if (modo === 'guardar') {
  mkdirSync(dirname(LINEA_BASE), { recursive: true })
  writeFileSync(LINEA_BASE, serializar(vivo))
  const vacias = Object.entries(vivo.mongo).filter(([, c]) => c.vacia).map(([n]) => n)
  console.log(`Esquema guardado en ${LINEA_BASE}: ${Object.keys(vivo.mongo).length} colecciones, ${Object.keys(vivo.redis.patrones).length} patrones de clave en Redis.`)
  if (vacias.length) console.log(`  aviso: sin documentos, solo se guardaron sus índices: ${vacias.join(', ')} (corre verificar-flujos.mjs antes de guardar)`)
  process.exit(0)
}

if (!existsSync(LINEA_BASE)) {
  console.error(`No hay línea base en ${LINEA_BASE}. Corre primero: node scripts/reduccion/esquema-datos.mjs guardar`)
  process.exit(2)
}
const base = JSON.parse(readFileSync(LINEA_BASE, 'utf8'))
const delMongo = diferencias(base.mongo, JSON.parse(serializar(vivo.mongo)), '$.mongo')
const delRedis = compararPatrones(base.redis.patrones, vivo.redis.patrones)
const canales = diferencias(base.redis.canales, vivo.redis.canales, '$.redis.canales')
for (const aviso of delRedis.avisos) console.log(`aviso (caducó entre las dos lecturas): redis ${aviso}`)
const todas = [...delMongo, ...delRedis.diferencias.map((d) => `redis ${d}`), ...canales]
if (todas.length) {
  console.log(`${todas.length} diferencias de esquema contra la línea base:`)
  for (const d of todas) console.log(`  ${d}`)
  process.exit(1)
}
console.log(`Esquema idéntico a la línea base (${Object.keys(vivo.mongo).length} colecciones, ${Object.keys(vivo.redis.patrones).length} patrones de Redis, 0 diferencias).`)
