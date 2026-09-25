const CLAVE = 'aguavigia.panel.token'
const eventos = new EventTarget()

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
  guardar(token: string): void {
    almacen()?.setItem(CLAVE, token)
    eventos.dispatchEvent(new Event('cambio'))
  },
  limpiar(): void {
    almacen()?.removeItem(CLAVE)
    eventos.dispatchEvent(new Event('cambio'))
  },
  alCambiar(callback: () => void): () => void {
    eventos.addEventListener('cambio', callback)
    return () => eventos.removeEventListener('cambio', callback)
  },
}
