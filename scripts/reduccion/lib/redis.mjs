// Lo mínimo de Redis que necesita esquema-datos.mjs, sin dependencias nuevas: el protocolo RESP por un socket (como
// scripts/simulacion/lib/redis.mjs) y la agrupación de claves por patrón. Solo se usan comandos de lectura.
import net from 'node:net'

/** Interpreta una respuesta RESP desde `desde`. Devuelve `{ valor, siguiente }` o `null` si el buffer aún no la trae completa. */
export function leerRespuesta(buffer, desde = 0) {
  if (desde >= buffer.length) return null
  const tipo = String.fromCharCode(buffer[desde])
  const finDeLinea = buffer.indexOf('\r\n', desde)
  if (finDeLinea < 0) return null
  const linea = buffer.toString('utf8', desde + 1, finDeLinea)
  const despuesDeLinea = finDeLinea + 2
  switch (tipo) {
    case '+':
      return { valor: linea, siguiente: despuesDeLinea }
    case '-':
      return { valor: new Error(linea), siguiente: despuesDeLinea }
    case ':':
      return { valor: Number(linea), siguiente: despuesDeLinea }
    case '$': {
      const largo = Number(linea)
      if (largo < 0) return { valor: null, siguiente: despuesDeLinea }
      if (buffer.length < despuesDeLinea + largo + 2) return null
      return { valor: buffer.toString('utf8', despuesDeLinea, despuesDeLinea + largo), siguiente: despuesDeLinea + largo + 2 }
    }
    case '*': {
      const cantidad = Number(linea)
      if (cantidad < 0) return { valor: null, siguiente: despuesDeLinea }
      const elementos = []
      let posicion = despuesDeLinea
      for (let i = 0; i < cantidad; i++) {
        const hijo = leerRespuesta(buffer, posicion)
        if (!hijo) return null
        elementos.push(hijo.valor)
        posicion = hijo.siguiente
      }
      return { valor: elementos, siguiente: posicion }
    }
    default:
      throw new Error(`respuesta RESP desconocida: ${tipo}`)
  }
}

export const codificarComando = (args) =>
  `*${args.length}\r\n${args.map((a) => `$${Buffer.byteLength(String(a))}\r\n${a}\r\n`).join('')}`

/** Un cliente de un solo hilo: manda un comando y espera su respuesta. */
export async function conectarRedis({ host = 'localhost', port = 6379, db = 0 } = {}) {
  const socket = net.createConnection({ host, port })
  await new Promise((ok, mal) => {
    socket.once('connect', ok)
    socket.once('error', mal)
  })
  let pendiente = Buffer.alloc(0)
  let esperando = null
  socket.on('data', (datos) => {
    pendiente = Buffer.concat([pendiente, datos])
    const respuesta = esperando && leerRespuesta(pendiente)
    if (respuesta) {
      pendiente = pendiente.subarray(respuesta.siguiente)
      const { ok, mal } = esperando
      esperando = null
      respuesta.valor instanceof Error ? mal(respuesta.valor) : ok(respuesta.valor)
    }
  })
  socket.on('error', (e) => esperando?.mal(e))
  const comando = (...args) =>
    new Promise((ok, mal) => {
      esperando = { ok, mal }
      socket.write(codificarComando(args))
    })
  await comando('SELECT', db)
  return { comando, cerrar: () => socket.destroy() }
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const HEX_LARGO = /^[0-9a-f]{16,}$/i
const IPV4 = /^\d{1,3}(\.\d{1,3}){3}$/

/** Sustituye por marcadores lo que identifica a una persona, un barrio o un instante, para agrupar claves por patrón. */
export function patronDeClave(clave, idsDeSector = new Set()) {
  // Los candados de ejecución única llevan el nombre de la tarea (ingesta, puesta-al-dia, ventanas…): son un solo patrón.
  if (clave.startsWith('tarea-unica:')) return 'tarea-unica:{nombre}'
  return clave
    .split(':')
    .map((segmento) => {
      if (idsDeSector.has(segmento)) return '{sectorId}'
      if (UUID.test(segmento) || HEX_LARGO.test(segmento)) return '{id}'
      if (IPV4.test(segmento)) return '{ip}'
      if (/^\d+$/.test(segmento)) return '{n}'
      return segmento
    })
    .join(':')
}

/** Agrupa claves por patrón y anota su tipo y si caducan (`con`), no caducan (`sin`) o hay de las dos (`mixta`). */
export function resumirClaves(claves) {
  const patrones = {}
  for (const { patron, tipo, ttl } of claves) {
    const caducidad = ttl >= 0 ? 'con' : 'sin'
    const actual = patrones[patron]
    if (!actual) patrones[patron] = { tipo, caducidad }
    else {
      if (actual.tipo !== tipo) actual.tipo = [...new Set([...actual.tipo.split('|'), tipo])].sort().join('|')
      if (actual.caducidad !== caducidad) actual.caducidad = 'mixta'
    }
  }
  return Object.fromEntries(Object.entries(patrones).sort(([a], [b]) => a.localeCompare(b)))
}

/**
 * Claves que existen solo mientras dura una operación corta (candados y reservas de unos segundos, bloqueos): según el instante de la
 * lectura están o no. Que aparezcan o desaparezcan es un aviso; que cambien de tipo o de caducidad sigue siendo una diferencia.
 */
export const PATRONES_TRANSITORIOS = new Set([
  'tarea-unica:{nombre}',
  'aguavigia:consenso:reserva:{sectorId}',
  'aguavigia:consenso:pendientes',
  'login:bloqueo:{id}',
])

/** Las entradas de la caché de Spring (`nombre::clave`) existen según alguien haya consultado ese dato antes de que expire. */
export const esEntradaDeCache = (patron) => patron.includes('::')

/**
 * Diferencias entre dos resúmenes de patrones. Un patrón que ya no está solo cuenta como diferencia si no caducaba (las claves
 * con TTL pueden haberse ido entre las dos lecturas); uno nuevo o con otro tipo o caducidad siempre cuenta.
 * @returns {{diferencias:string[], avisos:string[]}}
 */
export function compararPatrones(antes, despues) {
  const diferencias = []
  const avisos = []
  for (const patron of new Set([...Object.keys(antes), ...Object.keys(despues)])) {
    const a = antes[patron]
    const d = despues[patron]
    const transitorio = PATRONES_TRANSITORIOS.has(patron) || esEntradaDeCache(patron)
    if (!a) (transitorio ? avisos : diferencias).push(`${patron}: (ausente) → ${JSON.stringify(d)}`)
    else if (!d) (a.caducidad === 'sin' && !transitorio ? diferencias : avisos).push(`${patron}: ${JSON.stringify(a)} → (ausente)`)
    else if (a.tipo !== d.tipo || a.caducidad !== d.caducidad) diferencias.push(`${patron}: ${JSON.stringify(a)} → ${JSON.stringify(d)}`)
  }
  return { diferencias, avisos }
}
