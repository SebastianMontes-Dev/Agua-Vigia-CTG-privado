import assert from 'node:assert/strict'
import { test } from 'node:test'
import { Decimal128, Double, Int32, Long, ObjectId } from 'mongodb'
import { diferencias, formaBson, unir } from '../reduccion/lib/forma.mjs'
import { codificarComando, compararPatrones, leerRespuesta, patronDeClave, resumirClaves } from '../reduccion/lib/redis.mjs'

test('codificarComando arma el protocolo RESP con el largo en bytes', () => {
  assert.equal(codificarComando(['SELECT', 0]), '*2\r\n$6\r\nSELECT\r\n$1\r\n0\r\n')
  assert.equal(codificarComando(['GET', 'ñ']), '*2\r\n$3\r\nGET\r\n$2\r\nñ\r\n')
})

test('leerRespuesta entiende cadenas, enteros, nulos y arreglos anidados', () => {
  assert.deepEqual(leerRespuesta(Buffer.from('+OK\r\n')).valor, 'OK')
  assert.equal(leerRespuesta(Buffer.from(':-1\r\n')).valor, -1)
  assert.equal(leerRespuesta(Buffer.from('$-1\r\n')).valor, null)
  assert.equal(leerRespuesta(Buffer.from('$5\r\nhola!\r\n')).valor, 'hola!')
  const scan = leerRespuesta(Buffer.from('*2\r\n$1\r\n0\r\n*2\r\n$3\r\na:b\r\n$3\r\nc:d\r\n'))
  assert.deepEqual(scan.valor, ['0', ['a:b', 'c:d']])
})

test('leerRespuesta devuelve null si la respuesta llegó cortada', () => {
  assert.equal(leerRespuesta(Buffer.from('$10\r\nhola')), null)
  assert.equal(leerRespuesta(Buffer.from('*2\r\n$1\r\n0\r\n')), null)
  assert.equal(leerRespuesta(Buffer.from('+OK')), null)
})

test('leerRespuesta devuelve un Error para una respuesta de error', () => {
  assert.ok(leerRespuesta(Buffer.from('-ERR algo\r\n')).valor instanceof Error)
})

test('patronDeClave reemplaza barrios, identificadores, IP y números', () => {
  const barrios = new Set(['chambacu', 'la-matuna'])
  assert.equal(patronDeClave('consenso:sector:chambacu', barrios), 'consenso:sector:{sectorId}')
  assert.equal(patronDeClave('login:fallos:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08', barrios), 'login:fallos:{id}')
  assert.equal(patronDeClave('sesion:revocada:0ec3a98f-0ff2-4a9a-b650-2b63e1aa184e', barrios), 'sesion:revocada:{id}')
  assert.equal(patronDeClave('rl:172.18.0.1:reportes:12', barrios), 'rl:{ip}:reportes:{n}')
  assert.equal(patronDeClave('aguavigia:consenso:pendientes', barrios), 'aguavigia:consenso:pendientes')
  assert.equal(patronDeClave('tarea-unica:ventanas', barrios), 'tarea-unica:{nombre}')
  assert.equal(patronDeClave('tarea-unica:puesta-al-dia', barrios), 'tarea-unica:{nombre}')
})

test('resumirClaves agrupa por patrón y detecta caducidad mixta', () => {
  const resumen = resumirClaves([
    { patron: 'a:{id}', tipo: 'string', ttl: 30 },
    { patron: 'a:{id}', tipo: 'string', ttl: 10 },
    { patron: 'b', tipo: 'set', ttl: -1 },
    { patron: 'c', tipo: 'string', ttl: 5 },
    { patron: 'c', tipo: 'string', ttl: -1 },
  ])
  assert.deepEqual(resumen, {
    'a:{id}': { tipo: 'string', caducidad: 'con' },
    b: { tipo: 'set', caducidad: 'sin' },
    c: { tipo: 'string', caducidad: 'mixta' },
  })
})

test('compararPatrones: un patrón nuevo o con otro tipo es diferencia; uno que caducó es solo aviso', () => {
  const antes = {
    'a:{id}': { tipo: 'string', caducidad: 'con' },
    persistente: { tipo: 'set', caducidad: 'sin' },
    cambia: { tipo: 'string', caducidad: 'con' },
  }
  const despues = { persistente: { tipo: 'set', caducidad: 'sin' }, cambia: { tipo: 'hash', caducidad: 'con' }, nuevo: { tipo: 'string', caducidad: 'sin' } }
  const { diferencias: dif, avisos } = compararPatrones(antes, despues)
  assert.equal(avisos.length, 1)
  assert.match(avisos[0], /^a:\{id\}/)
  assert.deepEqual(dif.map((d) => d.split(':')[0]), ['cambia', 'nuevo'])
})

test('compararPatrones: un patrón sin caducidad que desaparece sí es diferencia', () => {
  const { diferencias: dif } = compararPatrones({ x: { tipo: 'set', caducidad: 'sin' } }, {})
  assert.equal(dif.length, 1)
})

test('formaBson distingue los tipos BSON que la forma JSON confunde', () => {
  const forma = formaBson({
    _id: new ObjectId('507f1f77bcf86cd799439011'),
    entero: new Int32(1),
    largo: Long.fromNumber(2),
    decimal: new Double(1.5),
    dinero: Decimal128.fromString('1.10'),
    cuando: new Date(),
    texto: 'a',
    nulo: null,
    lista: [new Int32(1)],
  })
  assert.deepEqual(forma, {
    _id: 'objectId',
    cuando: 'date',
    decimal: 'double',
    dinero: 'decimal',
    entero: 'int',
    largo: 'long',
    lista: { elementos: 'int', vacio: false },
    nulo: 'null',
    texto: 'string',
  })
})

test('un campo que pasa de int a long, o de string a objectId, se ve como diferencia', () => {
  const antes = formaBson({ n: new Int32(1), id: 'a' })
  const despues = formaBson({ n: Long.fromNumber(1), id: new ObjectId() })
  assert.deepEqual(diferencias(antes, despues), ['$.id: "string" → "objectId"', '$.n: "int" → "long"'])
})

test('unir documentos: un campo opcional aparece y uno null en algunos sale null|tipo', () => {
  const unido = unir(formaBson({ a: 'x', b: null }), formaBson({ a: 'y', b: 'z', c: new Int32(1) }))
  assert.deepEqual(unido, { a: 'string', b: 'null|string', c: 'int' })
})

test('compararPatrones: un candado transitorio que aparece o desaparece es solo un aviso, pero si cambia de caducidad es diferencia', () => {
  const candado = { tipo: 'string', caducidad: 'con' }
  const aparece = compararPatrones({}, { 'tarea-unica:{nombre}': candado })
  assert.deepEqual(aparece.diferencias, [])
  assert.equal(aparece.avisos.length, 1)
  const desaparece = compararPatrones({ 'tarea-unica:{nombre}': { tipo: 'string', caducidad: 'sin' } }, {})
  assert.deepEqual(desaparece.diferencias, [])
  const cambia = compararPatrones({ 'tarea-unica:{nombre}': candado }, { 'tarea-unica:{nombre}': { tipo: 'string', caducidad: 'sin' } })
  assert.equal(cambia.diferencias.length, 1)
})

test('compararPatrones: un patrón nuevo que no es transitorio sigue siendo diferencia', () => {
  assert.equal(compararPatrones({}, { 'clave-nueva:{id}': { tipo: 'string', caducidad: 'con' } }).diferencias.length, 1)
})

test('compararPatrones: una entrada de la caché de Spring que aparece o desaparece es solo un aviso', () => {
  const entrada = { tipo: 'string', caducidad: 'con' }
  assert.deepEqual(compararPatrones({}, { 'sectores::SimpleKey []': entrada }).diferencias, [])
  assert.deepEqual(compararPatrones({ 'sectores::SimpleKey []': { tipo: 'string', caducidad: 'sin' } }, {}).diferencias, [])
})
