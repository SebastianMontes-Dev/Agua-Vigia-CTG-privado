#!/usr/bin/env node
/**
 * Instantánea de la FORMA de las respuestas del backend (R0 de docs/reduccion). Lo que el contrato OpenAPI no ve: un campo que
 * antes venía `null` y ahora no viene, una cabecera que desaparece, un error que cambia de `type`.
 *
 *   node scripts/reduccion/instantanea.mjs guardar    # escribe scripts/reduccion/linea-base/forma.json
 *   node scripts/reduccion/instantanea.mjs comparar   # imprime cada diferencia; sale con 1 si hay alguna
 *
 * Recorre todos los GET públicos, los GET del panel (con la sesión de un ADMIN), una muestra de errores y, de cada respuesta,
 * guarda el estado, el tipo de contenido, qué cabeceras de contrato vienen y la forma del cuerpo (tipos, nunca valores).
 * Pensado para una base recién levantada con `docker compose up`: dos pasadas seguidas sobre la misma base dan lo mismo.
 *
 * Variables:
 *   API_URL        http://localhost:8081
 *   ADMIN_CORREO   admin@aguavigia.local
 *   ADMIN_CLAVE    la clave cuyo hash pusiste en VEEDOR_PASSWORD_HASH
 *   TOTP_SECRETO   el segundo factor del ADMIN, si ya lo dio de alta
 *   INSTANTANEA_NOMBRE  forma (por defecto) o forma-con-datos: nombre del archivo en linea-base/
 *   TOTP_ARCHIVO   archivo donde leer/guardar ese secreto (si no hay TOTP_SECRETO); se crea al dar de alta el segundo factor
 *   PROBAR_LIMITE  1 para provocar también un 429 y comprobar `Retry-After` (agota la cuota por IP de dispositivos durante una hora)
 *
 * Deja un reporte descartado y un dispositivo en la base (lo necesita para provocar el 415 de una foto WebP).
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { crearDispositivo, puntosPorBarrio } from '../lib/identidad-api.mjs'
import { iniciarSesionAdmin } from '../lib/sesion-admin.mjs'
import { diferencias, resumirRespuesta, serializar } from './lib/forma.mjs'

const AQUI = dirname(fileURLToPath(import.meta.url))
// INSTANTANEA_NOMBRE=forma-con-datos para la pasada que se hace después de verificar-flujos.mjs (cortes, reportes y eventos ya creados).
const LINEA_BASE = resolve(AQUI, 'linea-base', `${process.env.INSTANTANEA_NOMBRE ?? 'forma'}.json`)
const API = (process.env.API_URL ?? 'http://localhost:8081').replace(/\/$/, '')

const modo = process.argv[2]
if (!['guardar', 'comparar'].includes(modo)) {
  console.error('Uso: node scripts/reduccion/instantanea.mjs guardar|comparar')
  process.exit(2)
}

async function pedir(metodo, ruta, { token, cabeceras = {}, cuerpo, formulario } = {}) {
  const init = { method: metodo, headers: { Accept: 'application/json, text/csv;q=0.9, */*;q=0.1', ...cabeceras } }
  if (token) init.headers.Authorization = `Bearer ${token}`
  if (formulario) {
    init.body = formulario
  } else if (cuerpo !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(cuerpo)
  }
  const respuesta = await fetch(`${API}${ruta}`, init)
  const texto = await respuesta.text()
  return {
    estado: respuesta.status,
    contentType: respuesta.headers.get('content-type'),
    cabeceras: Object.fromEntries(respuesta.headers.entries()),
    texto,
    json: (() => {
      try {
        return texto ? JSON.parse(texto) : null
      } catch {
        return null
      }
    })(),
  }
}

/** Primer elemento de una respuesta de listado, sea un arreglo o un objeto con el arreglo dentro. */
function primero(json, ...claves) {
  if (Array.isArray(json)) return json[0]
  for (const clave of [...claves, 'contenido', 'items']) if (Array.isArray(json?.[clave])) return json[clave][0]
  return undefined
}

const resultado = {}
const anotar = (nombre, respuesta) => {
  resultado[nombre] = resumirRespuesta(respuesta)
  return respuesta
}

// --- GET del panel -------------------------------------------------------------------------------------------------------
const archivoTotp = process.env.TOTP_ARCHIVO
const secretoGuardado = process.env.TOTP_SECRETO ?? (archivoTotp && existsSync(archivoTotp) ? readFileSync(archivoTotp, 'utf8').trim() : undefined)
const sesion = await iniciarSesionAdmin({
  api: API,
  correo: process.env.ADMIN_CORREO ?? 'admin@aguavigia.local',
  clave: process.env.ADMIN_CLAVE,
  totpSecreto: secretoGuardado,
}).catch((e) => {
  console.error(`No se pudo iniciar sesión como ADMIN: ${e.message}`)
  process.exit(2)
})
if (sesion.secretoNuevo) {
  if (archivoTotp) {
    writeFileSync(archivoTotp, `${sesion.totpSecreto}\n`)
    console.error(`Segundo factor dado de alta; el secreto quedó en ${archivoTotp}.`)
  } else {
    console.error(`Segundo factor dado de alta. Para repetir: TOTP_SECRETO=${sesion.totpSecreto}`)
  }
}
const token = sesion.token


// El primer ciclo de ingesta corre ~60 s después de arrancar y puede publicar avisos, abrir propuestas y escribir en la bitácora. Una
// instantánea tomada antes y otra después serían distintas aunque nada haya cambiado: se espera a que termine antes de capturar nada.
{
  const limite = Date.now() + 240_000
  let listo = false
  while (!listo && Date.now() < limite) {
    const salud = await pedir('GET', '/api/veedor/ingesta/salud', { token })
    listo = Array.isArray(salud.json) && salud.json.length > 0 && salud.json.every((c) => c.ultimaEjecucionExitosa || c.ultimoFallo)
    if (!listo) await new Promise((ok) => setTimeout(ok, 2000))
  }
  if (!listo) {
    console.error('La ingesta no terminó su primer ciclo en 4 minutos; la instantánea no sería comparable.')
    process.exit(2)
  }
  await new Promise((ok) => setTimeout(ok, 5000)) // deja que se asienten los avisos y los eventos que el ciclo dejó en cola
}

// --- GET públicos --------------------------------------------------------------------------------------------------------
const sectores = anotar('GET /api/sectores', await pedir('GET', '/api/sectores'))
const sectorId = primero(sectores.json, 'sectores')?.id
anotar('GET /api/sectores/geometria', await pedir('GET', '/api/sectores/geometria', { cabeceras: { Accept: 'application/geo+json' } }))
if (sectorId) {
  anotar('GET /api/sectores/{id}', await pedir('GET', `/api/sectores/${sectorId}`))
  anotar('GET /api/sectores/{id}/cortes', await pedir('GET', `/api/sectores/${sectorId}/cortes`))
  anotar('GET /api/cumplimiento/sectores/{id}', await pedir('GET', `/api/cumplimiento/sectores/${sectorId}`))
}
anotar('GET /api/cumplimiento', await pedir('GET', '/api/cumplimiento'))
anotar('GET /api/cumplimiento/calidad', await pedir('GET', '/api/cumplimiento/calidad'))
anotar('GET /api/cumplimiento/serie', await pedir('GET', '/api/cumplimiento/serie'))
anotar('GET /api/cumplimiento/serie.csv', await pedir('GET', '/api/cumplimiento/serie.csv'))
anotar('GET /api/estadisticas', await pedir('GET', '/api/estadisticas'))
anotar('GET /api/estadisticas/exportar.csv', await pedir('GET', '/api/estadisticas/exportar.csv'))
const bitacora = anotar('GET /api/bitacora', await pedir('GET', '/api/bitacora?pagina=0&tamano=20'))
const eventoId = primero(bitacora.json)?.id
if (eventoId) anotar('GET /api/bitacora/{id}/sustento', await pedir('GET', `/api/bitacora/${eventoId}/sustento`))
anotar('GET /api/v2/requests.json', await pedir('GET', '/api/v2/requests.json'))
anotar('GET /api/sistema/modo', await pedir('GET', '/api/sistema/modo'))

// Se hace antes de leer las métricas: el contador por nivel de verificación solo existe una vez que el proceso ve un reporte verificado,
// y sin esto la primera instantánea y las siguientes diferirían por eso.
// El 415 de una foto WebP exige un reporte propio y su token de subida; el reporte se descarta enseguida para que no
// aparezca en la cola de moderación y la segunda pasada vea la misma base.
const WEBP = Buffer.from('UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA', 'base64')
try {
  const barrio = sectorId
  const punto = (await puntosPorBarrio(API)).get(barrio)
  const dispositivo = await crearDispositivo(API)
  const reporte = await pedir('POST', '/api/reportes', {
    cabeceras: { 'X-Dispositivo': dispositivo },
    cuerpo: { tipo: 'SIN_AGUA', sectorId: barrio, coordenada: punto, precisionMetros: 10 },
  })
  const formulario = new FormData()
  formulario.append('foto', new Blob([WEBP], { type: 'image/webp' }), 'prueba.webp')
  anotar(
    'error 415 foto WebP',
    await pedir('POST', `/api/reportes/${reporte.json.id}/foto`, {
      formulario,
      cabeceras: { 'X-Subida': reporte.json.subidaToken },
    }),
  )
  await pedir('PATCH', `/api/veedor/reportes/${reporte.json.id}/descartar`, { token })
} catch (e) {
  resultado['error 415 foto WebP'] = { sinSondear: String(e.message).slice(0, 160) }
}

anotar('GET /api/veedor/yo', await pedir('GET', '/api/veedor/yo', { token }))
anotar('GET /api/veedor/usuarios', await pedir('GET', '/api/veedor/usuarios?pagina=0&tamano=10', { token }))
anotar('GET /api/veedor/auditoria', await pedir('GET', '/api/veedor/auditoria', { token }))
const cortes = anotar('GET /api/veedor/cortes', await pedir('GET', `/api/veedor/cortes?sectorId=${sectorId}`, { token }))
const corteId = primero(cortes.json)?.id
if (corteId) {
  anotar('GET /api/veedor/cortes/{id}', await pedir('GET', `/api/veedor/cortes/${corteId}`, { token }))
  anotar('GET /api/cumplimiento/cortes/{id}', await pedir('GET', `/api/cumplimiento/cortes/${corteId}`))
}
anotar('GET /api/veedor/cortes/vencidos', await pedir('GET', '/api/veedor/cortes/vencidos', { token }))
anotar('GET /api/veedor/reportes/pendientes', await pedir('GET', '/api/veedor/reportes/pendientes', { token }))
anotar('GET /api/veedor/disputas', await pedir('GET', '/api/veedor/disputas', { token }))
anotar('GET /api/veedor/ingesta/propuestas', await pedir('GET', '/api/veedor/ingesta/propuestas', { token }))
anotar('GET /api/veedor/ingesta/salud', await pedir('GET', '/api/veedor/ingesta/salud', { token }))
anotar('GET /api/veedor/ingesta/fallidos', await pedir('GET', '/api/veedor/ingesta/fallidos', { token }))
anotar('GET /api/veedor/sistema/metricas', await pedir('GET', '/api/veedor/sistema/metricas', { token }))

// --- Muestra de errores --------------------------------------------------------------------------------------------------
anotar('error 404 sector inexistente', await pedir('GET', '/api/sectores/no-existe-xyz'))
anotar('error 400 filtro mal formado', await pedir('GET', '/api/bitacora?pagina=no-es-un-numero'))
anotar('error 401 panel sin sesión', await pedir('GET', '/api/veedor/yo'))
anotar('error 405 método no permitido', await pedir('DELETE', '/api/sectores'))

if (process.env.PROBAR_LIMITE === '1') {
  let limite
  for (let i = 0; i < 1100 && !limite; i++) {
    const r = await pedir('POST', '/api/dispositivos')
    if (r.estado === 429) limite = r
  }
  resultado['error 429 límite de dispositivos'] = limite ? resumirRespuesta(limite) : { sinSondear: 'no llegó a 429' }
}

// --- Guardar o comparar --------------------------------------------------------------------------------------------------
if (modo === 'guardar') {
  mkdirSync(dirname(LINEA_BASE), { recursive: true })
  writeFileSync(LINEA_BASE, serializar(resultado))
  console.log(`Instantánea guardada en ${LINEA_BASE} (${Object.keys(resultado).length} respuestas).`)
  process.exit(0)
}

if (!existsSync(LINEA_BASE)) {
  console.error(`No hay línea base en ${LINEA_BASE}. Corre primero: node scripts/reduccion/instantanea.mjs guardar`)
  process.exit(2)
}
const base = JSON.parse(readFileSync(LINEA_BASE, 'utf8'))
// El sondeo del 429 es opcional: sin PROBAR_LIMITE no se compara contra una línea base que lo tenga.
if (process.env.PROBAR_LIMITE !== '1') delete base['error 429 límite de dispositivos']
const encontradas = diferencias(base, JSON.parse(serializar(resultado)))
if (encontradas.length) {
  console.log(`${encontradas.length} diferencias de forma contra la línea base:`)
  for (const d of encontradas) console.log(`  ${d}`)
  process.exit(1)
}
console.log(`Forma idéntica a la línea base (${Object.keys(resultado).length} respuestas, 0 diferencias).`)
