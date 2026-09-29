import { useQuery } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'
import type { ErrorApi } from '../api/cliente'
import { pedirCuenta, type Cuenta, type Permiso } from '../api/panel'
import { sesion } from '../api/sesion'

/** Hay token o no; el panel entero se decide con esto y con lo que devuelve `/api/veedor/yo`. */
export function useToken(): string | null {
  return useSyncExternalStore(sesion.alCambiar, sesion.token)
}

/**
 * La cuenta vigente, tal como está en la base y no como la dejó el token (`/yo`). Solo se pide con una sesión completa:
 * con el alcance de alta del segundo factor, el servidor responde 403 a todo lo que no sea `/segundo-factor/**`.
 */
export function useCuenta() {
  const token = useToken()
  return useQuery<Cuenta, ErrorApi>({
    queryKey: ['cuenta', token],
    enabled: token !== null && sesion.alcance() === 'COMPLETO',
    queryFn: async ({ signal }) => {
      const resultado = await pedirCuenta(signal)
      if (!resultado.ok) throw resultado.error
      return resultado.datos
    },
    staleTime: 30_000,
  })
}

export function tienePermiso(cuenta: Pick<Cuenta, 'permisosEfectivos'> | undefined, permiso: Permiso): boolean {
  return cuenta?.permisosEfectivos?.includes(permiso) ?? false
}
