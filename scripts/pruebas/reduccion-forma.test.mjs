import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  diferencias,
  forma,
  normalizarContrato,
  operacionesDe,
  ordenar,
  resumirRespuesta,
  serializar,
  unir,
} from '../reduccion/lib/forma.mjs'

const contratoA = {
  openapi: '3.0.1',
  info: { title: 'AguaVigía', version: '1.0' },
  servers: [{ url: 'http://localhost:8081', description: 'local' }],
  paths: {
    '/api/sectores/{id}': {
      get: {
        operationId: 'obtener',
        tags: ['sectores'],
        summary: 'Un sector',
        description: 'Texto que puede cambiar',
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'string' }, example: 'x' },
          { name: 'idioma', in: 'query', schema: { type: 'string' } },
        ],
        responses: { 200: { description: 'ok', content: { 'application/json': { schema: { $ref: '#/components/schemas/Sector' } } } } },
      },
    },
  },
  components: {
    schemas: {
      Sector: {
        type: 'object',
        required: ['nombre', 'id'],
        properties: {
          id: { type: 'string', description: 'identificador' },
          nombre: { type: 'string', example: 'Bocagrande' },
          description: { type: 'string', nullable: true },
        },
      },
    },
  },
}

test('la normalización quita la redacción pero conserva rutas, parámetros y esquemas', () => {
  const normal = normalizarContrato(contratoA)
  assert.equal(normal.info, undefined)
  assert.equal(normal.servers, undefined)
  const operacion = normal.paths['/api/sectores/{id}'].get
  assert.equal(operacion.summary, undefined)
  assert.equal(operacion.description, undefined)
  assert.equal(operacion.operationId, 'obtener')
  assert.deepEqual(operacion.tags, ['sectores'])
  assert.equal(operacion.parameters[0].example, undefined)
  assert.equal(operacion.parameters[0].name, 'id')
  assert.equal(operacion.responses['200'].description, undefined)
})

test('un campo llamado «description» es contrato y no se quita; su redacción sí', () => {
  const propiedades = normalizarContrato(contratoA).components.schemas.Sector.properties
  assert.deepEqual(Object.keys(propiedades), ['description', 'id', 'nombre'])
  assert.equal(propiedades.id.description, undefined)
  assert.equal(propiedades.nombre.example, undefined)
  assert.equal(propiedades.description.nullable, true)
})

test('el orden de las claves, de los parámetros y de required no cuenta', () => {
  const otro = structuredClone(contratoA)
  otro.paths['/api/sectores/{id}'].get.parameters.reverse()
  otro.components.schemas.Sector.required = ['id', 'nombre']
  otro.info.version = '9.9'
  assert.deepEqual(diferencias(normalizarContrato(contratoA), normalizarContrato(otro)), [])
})

test('un cambio de contrato se reporta con su ruta, antes y después', () => {
  const cambiado = structuredClone(contratoA)
  cambiado.components.schemas.Sector.properties.nombre.type = 'integer'
  cambiado.paths['/api/sectores/{id}'].get.operationId = 'otro'
  delete cambiado.components.schemas.Sector.properties.id
  const lista = diferencias(normalizarContrato(contratoA), normalizarContrato(cambiado))
  assert.deepEqual(lista, [
    '$.components.schemas.Sector.properties.id: {"type":"string"} → (ausente)',
    '$.components.schemas.Sector.properties.nombre.type: "string" → "integer"',
    '$.paths./api/sectores/{id}.get.operationId: "obtener" → "otro"',
  ])
})

test('operacionesDe lista método y ruta y omite /api/sim', () => {
  const doc = {
    paths: {
      '/api/b': { post: {}, get: {} },
      '/api/a': { get: {} },
      '/api/sim/x': { post: {} },
      '/actuator/health': { get: {} },
    },
  }
  assert.deepEqual(operacionesDe(doc), ['GET /api/a', 'GET /api/b', 'POST /api/b'])
})

test('forma reemplaza los valores por su tipo', () => {
  assert.deepEqual(forma({ b: 'abc', a: 3, c: null, d: true }), { a: 'number', b: 'string', c: 'null', d: 'boolean' })
})

test('forma de un arreglo une los elementos y marca si está vacío', () => {
  assert.deepEqual(forma([]), { elementos: null, vacio: true })
  assert.deepEqual(forma([{ x: 1 }, { x: 2, y: 'a' }]), { elementos: { x: 'number', y: 'string' }, vacio: false })
})

test('un campo null en un elemento y texto en otro da «null|string», sin depender del orden', () => {
  const uno = forma([{ f: null }, { f: 'a' }])
  const otro = forma([{ f: 'a' }, { f: null }])
  assert.deepEqual(uno, otro)
  assert.equal(uno.elementos.f, 'null|string')
})

test('una clave que falta en algún elemento sigue apareciendo en la forma unida', () => {
  assert.deepEqual(unir({ a: 'string' }, { b: 'number' }), { a: 'string', b: 'number' })
})

test('forma detecta que un null pase a ausente', () => {
  const antes = forma({ id: 'x', fin: null })
  const despues = forma({ id: 'x' })
  assert.deepEqual(diferencias(antes, despues), ['$.fin: "null" → (ausente)'])
})

test('serializar es estable aunque cambie el orden de las claves', () => {
  assert.equal(serializar({ b: 1, a: { d: 1, c: 2 } }), serializar({ a: { c: 2, d: 1 }, b: 1 }))
  assert.deepEqual(ordenar({ b: [{ z: 1, y: 2 }], a: 1 }), { a: 1, b: [{ y: 2, z: 1 }] })
})

test('resumirRespuesta de un listado paginado anota las cabeceras de contrato', () => {
  const resumen = resumirRespuesta({
    estado: 200,
    contentType: 'application/json',
    cabeceras: { 'x-total-count': '5', link: '<a>; rel="next"' },
    texto: '[{"id":"a"}]',
  })
  assert.deepEqual(resumen.cabeceras, { 'x-total-count': 'presente', link: 'presente', 'retry-after': 'ausente' })
  assert.deepEqual(resumen.cuerpo, { elementos: { id: 'string' }, vacio: false })
})

test('resumirRespuesta de un error RFC 7807 conserva su type sin el prefijo del servidor', () => {
  const resumen = resumirRespuesta({
    estado: 404,
    contentType: 'application/problem+json;charset=UTF-8',
    cabeceras: {},
    texto: '{"type":"https://aguavigia.example/errores/recurso-no-encontrado","status":404,"detail":"no existe"}',
  })
  assert.equal(resumen.estado, 404)
  assert.equal(resumen.contentType, 'application/problem+json')
  assert.equal(resumen.tipo, 'recurso-no-encontrado')
  assert.deepEqual(resumen.cuerpo, { detail: 'string', status: 'number', type: 'string' })
})

test('resumirRespuesta de un CSV conserva solo la fila de títulos', () => {
  const resumen = resumirRespuesta({
    estado: 200,
    contentType: 'text/csv;charset=UTF-8',
    cabeceras: {},
    texto: 'sector,total\r\nA,3\r\n',
  })
  assert.deepEqual(resumen.cuerpo, { titulos: 'sector,total' })
})

test('resumirRespuesta tolera un JSON ilegible y un cuerpo vacío', () => {
  assert.equal(resumirRespuesta({ estado: 200, contentType: 'application/json', cabeceras: {}, texto: '{no' }).cuerpo, 'json-ilegible')
  assert.equal(resumirRespuesta({ estado: 204, contentType: null, cabeceras: {}, texto: '' }).cuerpo, 'vacio')
})
