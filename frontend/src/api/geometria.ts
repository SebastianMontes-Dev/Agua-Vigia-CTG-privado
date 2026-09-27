import type { FeatureCollection, MultiPolygon, Polygon } from 'geojson'

export type GeometriaSectores = FeatureCollection<Polygon | MultiPolygon, { nombre?: string }>

interface Guardada { geometria: GeometriaSectores; guardadaEn: number }

export interface AlmacenGeometria {
  leer: () => Promise<Guardada | null>
  guardar: (valor: Guardada) => Promise<void>
}

// El servidor la marca cacheable un día y solo cambia al volver a sembrar (docs/api/sectores-y-tiempo-real.md).
export const VIGENCIA_GEOMETRIA_MS = 24 * 60 * 60_000

function esGeometria(valor: unknown): valor is GeometriaSectores {
  const coleccion = valor as GeometriaSectores | null
  return coleccion?.type === 'FeatureCollection' && Array.isArray(coleccion.features)
}

/**
 * Una sola descarga por día y dispositivo. Si la red falla se usa la copia vencida: los barrios no se mueven,
 * lo que caduca es el estado, y ese viaja aparte.
 */
export async function cargarGeometria(
  almacen: AlmacenGeometria,
  pedir: typeof fetch = fetch,
  ahora: () => number = Date.now,
): Promise<GeometriaSectores> {
  const guardada = await almacen.leer().catch(() => null)
  if (guardada && ahora() - guardada.guardadaEn < VIGENCIA_GEOMETRIA_MS) return guardada.geometria
  try {
    // Sin `Accept: application/json`: con esa cabecera el servidor responde un 404 engañoso.
    const respuesta = await pedir('/api/sectores/geometria')
    if (!respuesta.ok) throw new Error(`geometria ${respuesta.status}`)
    const geometria: unknown = await respuesta.json()
    if (!esGeometria(geometria)) throw new Error('geometria inválida')
    await almacen.guardar({ geometria, guardadaEn: ahora() }).catch(() => undefined)
    return geometria
  } catch (error) {
    if (guardada) return guardada.geometria
    throw error
  }
}

const BASE = 'aguavigia'
const TABLA = 'geometria'
const CLAVE = 'sectores'

function abrir(): Promise<IDBDatabase> {
  return new Promise((resolver, rechazar) => {
    const peticion = indexedDB.open(BASE, 1)
    peticion.onupgradeneeded = () => peticion.result.createObjectStore(TABLA)
    peticion.onsuccess = () => resolver(peticion.result)
    peticion.onerror = () => rechazar(peticion.error)
  })
}

function operar<T>(modo: IDBTransactionMode, accion: (tabla: IDBObjectStore) => IDBRequest): Promise<T> {
  return abrir().then((base) => new Promise<T>((resolver, rechazar) => {
    const transaccion = base.transaction(TABLA, modo)
    const peticion = accion(transaccion.objectStore(TABLA))
    transaccion.oncomplete = () => { base.close(); resolver(peticion.result as T) }
    transaccion.onerror = () => { base.close(); rechazar(transaccion.error) }
  }))
}

export function almacenIndexedDb(): AlmacenGeometria {
  return {
    leer: async () => (typeof indexedDB === 'undefined' ? null : (await operar<Guardada | undefined>('readonly', (t) => t.get(CLAVE))) ?? null),
    guardar: async (valor) => { if (typeof indexedDB !== 'undefined') await operar('readwrite', (t) => t.put(valor, CLAVE)) },
  }
}
