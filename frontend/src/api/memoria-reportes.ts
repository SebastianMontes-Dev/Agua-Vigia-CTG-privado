const CLAVE = 'aguavigia.reportes'
const MAXIMO = 50

interface Memoria { propios: string[]; confirmados: string[] }
type Almacen = Pick<Storage, 'getItem' | 'setItem'> | null

const VACIA: Memoria = { propios: [], confirmados: [] }

// Solo cuando el almacenamiento está bloqueado: entonces se recuerda durante esta visita.
let enMemoria: Memoria = VACIA

function almacenLocal(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

function leer(almacen: Almacen): Memoria {
  let texto: string | null
  try {
    if (!almacen) return enMemoria
    texto = almacen.getItem(CLAVE)
  } catch {
    return enMemoria
  }
  try {
    const guardada = JSON.parse(texto ?? 'null') as Partial<Memoria> | null
    if (guardada && Array.isArray(guardada.propios) && Array.isArray(guardada.confirmados)) {
      return { propios: guardada.propios, confirmados: guardada.confirmados }
    }
  } catch {
    // Un valor dañado equivale a no recordar nada.
  }
  return VACIA
}

function anotar(lista: 'propios' | 'confirmados', id: string, almacen: Almacen) {
  const memoria = leer(almacen)
  if (memoria[lista].includes(id)) return
  const nueva = { ...memoria, [lista]: [id, ...memoria[lista]].slice(0, MAXIMO) }
  try {
    if (!almacen) throw new Error('sin almacenamiento')
    almacen.setItem(CLAVE, JSON.stringify(nueva))
  } catch {
    enMemoria = nueva
  }
}

/**
 * El backend responde 200 sin contar nada cuando confirma quien envió el reporte o quien ya lo confirmó
 * (`ReporteCiudadano.confirmar`): la respuesta no lo distingue, así que el dispositivo lo recuerda para decirlo.
 */
export function recordarPropio(id: string, almacen: Almacen = almacenLocal()) { anotar('propios', id, almacen) }
export function recordarConfirmado(id: string, almacen: Almacen = almacenLocal()) { anotar('confirmados', id, almacen) }
export function esPropio(id: string, almacen: Almacen = almacenLocal()) { return leer(almacen).propios.includes(id) }
export function yaConfirmado(id: string, almacen: Almacen = almacenLocal()) { return leer(almacen).confirmados.includes(id) }
