import { useEffect, useState } from 'react'

/** Reloj de la interfaz para los «hace X»: avanza cada 30 s sin volver a pedir nada al servidor. */
export function useAhora(intervaloMs = 30_000): Date {
  const [ahora, setAhora] = useState(() => new Date())
  useEffect(() => {
    const id = setInterval(() => setAhora(new Date()), intervaloMs)
    return () => clearInterval(id)
  }, [intervaloMs])
  return ahora
}
