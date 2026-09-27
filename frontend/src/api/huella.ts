const CLAVE = 'aguavigia.huella'
let enMemoria: string | null = null

function hex(bytes: ArrayBuffer): string {
  return [...new Uint8Array(bytes)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
}

/**
 * Plan §6.4: una sola huella por dispositivo, aleatoria y sin derivar de nada identificable. Una huella nueva en cada
 * envío evadiría el cupo de 3 reportes por sector, así que se guarda y se reutiliza siempre.
 */
export async function obtenerHuella(almacen: Pick<Storage, 'getItem' | 'setItem'> | null = almacenLocal()): Promise<string> {
  const guardada = almacen?.getItem(CLAVE) ?? enMemoria
  if (guardada && /^[0-9a-f]{64}$/.test(guardada)) return guardada
  const sal = crypto.getRandomValues(new Uint8Array(16))
  const semilla = new TextEncoder().encode(`${crypto.randomUUID()}:${hex(sal.buffer)}`)
  const huella = hex(await crypto.subtle.digest('SHA-256', semilla))
  try {
    almacen?.setItem(CLAVE, huella)
  } catch {
    // Con el almacenamiento bloqueado la huella vale para esta visita.
  }
  enMemoria = huella
  return huella
}

function almacenLocal(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}
