import { useEffect, useLayoutEffect, useState } from 'react'
import { formatearNumero } from '../../dominio/formato'
import type { Veredicto } from '../../api/cuentas'

/**
 * El token del correo se captura una vez y sale de la URL de inmediato (guía §5.3): así no queda en el historial ni viaja
 * en la cabecera `Referer` de ningún enlace posterior. Vive solo en la memoria del componente.
 */
export function useTokenDeEnlace(token: string | undefined): string | undefined {
  const [capturado] = useState(token)
  useLayoutEffect(() => {
    const url = new URL(window.location.href)
    if (!url.searchParams.has('token')) return
    url.searchParams.delete('token')
    window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`)
  }, [])
  return capturado
}

/** Mientras la página está abierta, el navegador no manda referer a nadie (docs/api/cuentas-y-sesion.md). */
export function useSinReferrer(): void {
  useEffect(() => {
    const meta = document.createElement('meta')
    meta.name = 'referrer'
    meta.content = 'no-referrer'
    document.head.append(meta)
    return () => meta.remove()
  }, [])
}

export const SIN_RED = 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.'
export const FALLO = 'Algo falló de nuestro lado. Inténtalo otra vez en un momento.'

export function mensajeDeEspera(segundos: number | null): string {
  return segundos === null
    ? 'Hay demasiados intentos desde esta conexión. Espera unos minutos.'
    : `Hay demasiados intentos desde esta conexión. Espera ${formatearNumero(Math.max(1, Math.ceil(segundos / 60)))} min.`
}

/** Los mensajes comunes de un veredicto que no es «listo» ni «inválido» (cada pantalla explica su propio «inválido»). */
export function mensajeGeneral(veredicto: Veredicto): string | null {
  switch (veredicto.tipo) {
    case 'esperar': return mensajeDeEspera(veredicto.segundos)
    case 'sin-red': return SIN_RED
    case 'fallo': return FALLO
    default: return null
  }
}
