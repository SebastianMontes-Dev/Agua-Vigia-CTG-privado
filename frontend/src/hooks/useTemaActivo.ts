import { useEffect, useState } from 'react'

type Tema = 'claro' | 'oscuro'

function leerTemaActivo(): Tema {
  const explicito = document.documentElement.dataset.theme
  if (explicito === 'dark') return 'oscuro'
  if (explicito === 'light') return 'claro'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'oscuro' : 'claro'
}

/**
 * Lee el tema que el navegador está pintando de verdad — `data-theme` en `:root` si el usuario
 * ya eligió uno con el interruptor (ver useTheme, App.tsx), o `prefers-color-scheme` si no.
 *
 * A diferencia de `useTheme()`, es de solo lectura: no escribe `data-theme` ni `localStorage`.
 * `useTheme()` está pensado para un único controlador en la raíz de la app (el interruptor de
 * tema); llamarlo también desde componentes que solo necesitan *saber* el tema activo —como
 * `InsigniaEstado`, que se instancia muchas veces por página— duplicaría esa gestión de estado
 * sin necesidad. Reacciona tanto a alternar el tema (MutationObserver sobre `data-theme`) como a
 * un cambio de preferencia del sistema operativo cuando no hay elección explícita (matchMedia).
 */
export function useTemaActivo(): Tema {
  const [tema, setTema] = useState<Tema>(() => (typeof document === 'undefined' ? 'claro' : leerTemaActivo()))

  useEffect(() => {
    const actualizar = () => setTema(leerTemaActivo())

    const observer = new MutationObserver(actualizar)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

    const media = window.matchMedia('(prefers-color-scheme: dark)')
    media.addEventListener('change', actualizar)

    return () => {
      observer.disconnect()
      media.removeEventListener('change', actualizar)
    }
  }, [])

  return tema
}
