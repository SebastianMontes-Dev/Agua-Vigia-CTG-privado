export const CLAVE_HUELLA = 'aguavigia.huella'
export function crearProveedorHuella(almacen: Pick<Storage, 'getItem' | 'setItem'>, criptografia: Crypto) {
  let pendiente: Promise<string> | null = null
  return () => pendiente ??= (async () => {
    const guardada = almacen.getItem(CLAVE_HUELLA)
    if (guardada && /^[a-f0-9]{64}$/.test(guardada)) return guardada
    const sal = criptografia.getRandomValues(new Uint8Array(32))
    const contenido = new TextEncoder().encode(`${criptografia.randomUUID()}:${Array.from(sal).join(',')}`)
    const digest = await criptografia.subtle.digest('SHA-256', contenido)
    const huella = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
    almacen.setItem(CLAVE_HUELLA, huella)
    return huella
  })()
}
let proveedor: ReturnType<typeof crearProveedorHuella> | null = null
export function huellaDispositivo() {
  proveedor ??= crearProveedorHuella(localStorage, crypto)
  return proveedor()
}
