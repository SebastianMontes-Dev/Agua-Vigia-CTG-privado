const CLAVE = 'aguavigia.panel.token'
const CLAVE_ALCANCE = 'aguavigia.panel.alcance'
const eventos = new EventTarget()

/** `ALTA_SEGUNDO_FACTOR`: el token solo sirve para dar de alta el TOTP de un ADMIN (cuentas-y-sesion.md). */
export type Alcance = 'COMPLETO' | 'ALTA_SEGUNDO_FACTOR'

// sessionStorage y no localStorage: el panel se abre en equipos compartidos y la sesión debe morir con la pestaña.
function almacen(): Storage | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

export const sesion = {
  token(): string | null {
    return almacen()?.getItem(CLAVE) ?? null
  },
  // GET /api/veedor/yo no devuelve el alcance: se guarda el que trajo la sesión para saber adónde llevar al recargar.
  alcance(): Alcance | null {
    if (!sesion.token()) return null
    return almacen()?.getItem(CLAVE_ALCANCE) === 'ALTA_SEGUNDO_FACTOR' ? 'ALTA_SEGUNDO_FACTOR' : 'COMPLETO'
  },
  guardar(token: string, alcance: Alcance = 'COMPLETO'): void {
    almacen()?.setItem(CLAVE, token)
    almacen()?.setItem(CLAVE_ALCANCE, alcance)
    eventos.dispatchEvent(new Event('cambio'))
  },
  limpiar(): void {
    almacen()?.removeItem(CLAVE)
    almacen()?.removeItem(CLAVE_ALCANCE)
    eventos.dispatchEvent(new Event('cambio'))
  },
  alCambiar(callback: () => void): () => void {
    eventos.addEventListener('cambio', callback)
    return () => eventos.removeEventListener('cambio', callback)
  },
}
