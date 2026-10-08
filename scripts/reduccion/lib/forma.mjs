// Piezas puras que comparten comparar-contrato.mjs e instantanea.mjs (docs/reduccion/R0-red-de-seguridad.md).
// Sin red ni disco: así se prueban con `node --test` sin levantar nada.

/** Claves cuyo texto es redacción y no contrato; se quitan del documento OpenAPI antes de comparar. */
const CLAVES_DE_REDACCION = new Set(['servers', 'description', 'summary', 'example', 'examples'])

/** Claves cuyo valor es un mapa de nombres propios (de campos, de esquemas, de rutas…): sus hijos no son palabras clave. */
const MAPAS_DE_NOMBRES = new Set(['properties', 'schemas', 'paths', 'responses', 'headers', 'content', 'securitySchemes'])

/** Arreglos cuyo orden no cambia el significado y que springdoc puede emitir en otro orden. */
const ARREGLOS_SIN_ORDEN = new Set(['required'])

const comoTexto = (valor) => JSON.stringify(valor)

/** Ordena las claves de objetos de forma recursiva, para que dos documentos iguales den el mismo texto. */
export function ordenar(valor) {
  if (Array.isArray(valor)) return valor.map(ordenar)
  if (valor && typeof valor === 'object') {
    return Object.fromEntries(
      Object.keys(valor)
        .sort()
        .map((clave) => [clave, ordenar(valor[clave])]),
    )
  }
  return valor
}

function limpiar(valor, nombre, dentroDeMapaDeNombres) {
  if (Array.isArray(valor)) {
    const elementos = valor.map((e) => limpiar(e, undefined, false))
    if (nombre === 'parameters') {
      return elementos.sort((a, b) => `${a.in}:${a.name ?? a.$ref}`.localeCompare(`${b.in}:${b.name ?? b.$ref}`))
    }
    if (ARREGLOS_SIN_ORDEN.has(nombre)) return [...elementos].sort()
    return elementos
  }
  if (valor && typeof valor === 'object') {
    const salida = {}
    for (const [clave, hijo] of Object.entries(valor)) {
      // Dentro de `properties` las claves son nombres de campo: un campo llamado «description» sí es contrato.
      if (!dentroDeMapaDeNombres && CLAVES_DE_REDACCION.has(clave)) continue
      salida[clave] = limpiar(hijo, clave, MAPAS_DE_NOMBRES.has(clave) && !dentroDeMapaDeNombres)
    }
    return salida
  }
  return valor
}

/**
 * Deja del documento de springdoc solo lo que es contrato: rutas, métodos, operationId, tags, parámetros, requestBody,
 * respuestas, esquemas y seguridad. Quita `info` (título y versión) y la redacción.
 */
export function normalizarContrato(documento) {
  const { info: _info, ...resto } = documento
  return ordenar(limpiar(resto, undefined, false))
}

/**
 * Reemplaza los valores por su forma, para que el resultado no dependa de ids ni de horas.
 * `"abc"` → `"string"`, `3` → `"number"`, `null` → `"null"`; un objeto conserva sus claves (ordenadas); un arreglo da
 * `{ elementos: <forma unida de todos>, vacio }`. Se unen todos los elementos (y no solo el primero) para que un campo
 * que es `null` en un elemento y texto en otro dé `"null|string"` en vez de depender del orden de la lista.
 */
export function forma(valor) {
  if (valor === null) return 'null'
  if (Array.isArray(valor)) {
    const formas = valor.map(forma)
    return { elementos: formas.length ? formas.reduce(unir) : null, vacio: formas.length === 0 }
  }
  if (typeof valor === 'object') {
    return Object.fromEntries(
      Object.keys(valor)
        .sort()
        .map((clave) => [clave, forma(valor[clave])]),
    )
  }
  return typeof valor
}

/** Une dos formas: tipos escalares distintos pasan a «a|b»; los objetos se unen clave a clave. */
export function unir(a, b) {
  if (a === undefined) return b
  if (b === undefined) return a
  const esObjeto = (x) => x && typeof x === 'object' && !('elementos' in x)
  const esArreglo = (x) => x && typeof x === 'object' && 'elementos' in x
  if (esArreglo(a) && esArreglo(b)) {
    return { elementos: a.elementos === null ? b.elementos : b.elementos === null ? a.elementos : unir(a.elementos, b.elementos), vacio: a.vacio && b.vacio }
  }
  if (esObjeto(a) && esObjeto(b)) {
    const claves = [...new Set([...Object.keys(a), ...Object.keys(b)])].sort()
    return Object.fromEntries(claves.map((c) => [c, unir(a[c], b[c])]))
  }
  if (typeof a === 'string' && typeof b === 'string') {
    return [...new Set([...a.split('|'), ...b.split('|')])].sort().join('|')
  }
  return `${comoTexto(a)}|${comoTexto(b)}`
}

const AUSENTE = '(ausente)'

const resumir = (valor) => {
  const texto = valor === undefined ? AUSENTE : comoTexto(valor)
  return texto.length > 120 ? `${texto.slice(0, 117)}…` : texto
}

/** Cada diferencia entre dos JSON como `ruta: antes → después`. Vacío si son iguales. */
export function diferencias(antes, despues, ruta = '$') {
  if (comoTexto(antes) === comoTexto(despues)) return []
  const esObjeto = (x) => x && typeof x === 'object'
  if (esObjeto(antes) && esObjeto(despues) && Array.isArray(antes) === Array.isArray(despues)) {
    if (Array.isArray(antes)) {
      const salida = []
      const largo = Math.max(antes.length, despues.length)
      for (let i = 0; i < largo; i++) salida.push(...diferencias(antes[i], despues[i], `${ruta}[${i}]`))
      return salida
    }
    const claves = [...new Set([...Object.keys(antes), ...Object.keys(despues)])].sort()
    return claves.flatMap((clave) => diferencias(antes[clave], despues[clave], `${ruta}.${clave}`))
  }
  return [`${ruta}: ${resumir(antes)} → ${resumir(despues)}`]
}

/** Texto estable de un documento, para guardarlo en disco y que git muestre diferencias legibles. */
export const serializar = (documento) => `${JSON.stringify(ordenar(documento), null, 2)}\n`

const METODOS = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options']

/** «MÉTODO /ruta» de cada operación del documento, ordenadas. Se omite `/api/sim/**`, que solo existe en la instancia de simulación. */
export function operacionesDe(documento) {
  return Object.entries(documento.paths ?? {})
    .filter(([ruta]) => !ruta.startsWith('/api/sim/') && ruta.startsWith('/api/'))
    .flatMap(([ruta, operaciones]) =>
      METODOS.filter((m) => operaciones[m]).map((m) => `${m.toUpperCase()} ${ruta}`),
    )
    .sort()
}

/** Cabeceras cuya presencia es parte del contrato (paginación, enlaces, reintento). */
export const CABECERAS_DE_CONTRATO = ['x-total-count', 'link', 'retry-after']

const tipoDeMedio = (contentType) => (contentType ?? '').split(';')[0].trim().toLowerCase() || null

/**
 * Resume una respuesta HTTP en lo que la instantánea compara: el estado, el tipo de contenido, qué cabeceras de contrato
 * vienen y la forma del cuerpo. Un error RFC 7807 conserva su `type` (sin el prefijo del servidor). Un CSV conserva su fila
 * de títulos; cualquier otro texto, solo que lo es.
 *
 * @param {{estado:number, contentType:string|null, cabeceras:Record<string,string>, texto:string}} r
 */
export function resumirRespuesta({ estado, contentType, cabeceras, texto }) {
  const tipoMedio = tipoDeMedio(contentType)
  const resumen = {
    estado,
    contentType: tipoMedio,
    cabeceras: Object.fromEntries(CABECERAS_DE_CONTRATO.map((c) => [c, c in cabeceras ? 'presente' : 'ausente'])),
  }
  if (tipoMedio?.includes('json')) {
    let cuerpo
    try {
      cuerpo = texto ? JSON.parse(texto) : null
    } catch {
      return { ...resumen, cuerpo: 'json-ilegible' }
    }
    if (tipoMedio.includes('problem') && cuerpo && typeof cuerpo.type === 'string') {
      resumen.tipo = cuerpo.type.replace(/^.*\/errores\//, '')
    }
    resumen.cuerpo = forma(cuerpo)
  } else if (tipoMedio?.includes('csv')) {
    resumen.cuerpo = { titulos: texto.split(/\r?\n/, 1)[0] }
  } else {
    resumen.cuerpo = texto ? 'texto' : 'vacio'
  }
  return resumen
}

/**
 * Como `forma`, pero para documentos leídos de Mongo con `promoteValues: false`: distingue los tipos BSON
 * (Int32, Long, Double, Decimal128, ObjectId, Binary, Date…). Un campo que cambia de `int` a `long` o de `string` a `objectId`
 * rompe a quien lo lea, aunque el JSON de la API salga igual.
 */
export function formaBson(valor) {
  if (valor === null || valor === undefined) return 'null'
  if (Array.isArray(valor)) {
    const formas = valor.map(formaBson)
    return { elementos: formas.length ? formas.reduce(unir) : null, vacio: formas.length === 0 }
  }
  if (valor instanceof Date) return 'date'
  if (typeof valor === 'object' && typeof valor._bsontype === 'string') {
    const tipo = valor._bsontype
    return { Int32: 'int', Long: 'long', Double: 'double', Decimal128: 'decimal', ObjectId: 'objectId', Binary: 'binData' }[tipo] ?? tipo.toLowerCase()
  }
  if (typeof valor === 'object') {
    return Object.fromEntries(
      Object.keys(valor)
        .sort()
        .map((clave) => [clave, formaBson(valor[clave])]),
    )
  }
  return typeof valor
}
