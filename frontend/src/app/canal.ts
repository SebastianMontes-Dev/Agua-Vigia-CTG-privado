import { useEffect, useSyncExternalStore } from 'react'
import { crearCanal, entornoNavegador } from '../api/canal-en-vivo'
export const canal = crearCanal(entornoNavegador())
export function useCanal() {
  const lectura = useSyncExternalStore(canal.suscribir, canal.lectura)
  useEffect(() => { canal.iniciar(); return canal.detener }, [])
  return lectura
}
export function useVisible() {
  return useSyncExternalStore((avisar) => {
    document.addEventListener('visibilitychange', avisar)
    return () => document.removeEventListener('visibilitychange', avisar)
  }, () => !document.hidden)
}
