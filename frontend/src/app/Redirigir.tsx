import { useNavigate, type NavigateOptions } from '@tanstack/react-router'
import { useEffect, useRef } from 'react'

/**
 * Navega una sola vez. `<Navigate>` vuelve a navegar en cada render mientras la pantalla de origen siga montada, y
 * al perder la sesión el panel se vuelve a pintar mientras llega la de destino: eso era un bucle de renders.
 */
export function Redirigir({ destino }: { destino: NavigateOptions }) {
  const navegar = useNavigate()
  const hecho = useRef(false)
  useEffect(() => {
    if (hecho.current) return
    hecho.current = true
    void navegar({ ...destino, replace: true })
  }, [navegar, destino])
  return null
}
