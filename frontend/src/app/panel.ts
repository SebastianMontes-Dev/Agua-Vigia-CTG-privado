import { useQuery } from '@tanstack/react-query'
import { useState, useSyncExternalStore } from 'react'
import { consultarCuentaActual } from '../api/panel'
import { sesion, type Alcance } from '../api/sesion'
import { clienteConsultas } from './datos'

// Lo del panel pertenece a una sesión: al entrar, salir o cambiar de cuenta no se reutiliza nada de la anterior.
sesion.alCambiar(() => clienteConsultas.removeQueries({ queryKey: ['panel'] }))

export function useAlcance(): Alcance | null {
  return useSyncExternalStore(sesion.alCambiar, sesion.alcance, () => null)
}

/** Distingue «nunca hubo sesión» de «la sesión terminó mientras la pantalla estaba abierta». */
export function useSesionPerdida(alcance: Alcance | null): boolean {
  const [tuvoSesion, setTuvoSesion] = useState(alcance !== null)
  if (alcance !== null && !tuvoSesion) setTuvoSesion(true)
  return tuvoSesion && alcance === null
}

/** Permisos vigentes en la base de datos, no los que dice el token (cuentas-y-sesion.md, «Al recargar»). */
export function useCuentaActual(habilitada: boolean) {
  return useQuery({
    queryKey: ['panel', 'yo'],
    enabled: habilitada,
    queryFn: consultarCuentaActual,
    staleTime: 60_000,
  })
}
