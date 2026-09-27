import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { I18nProvider } from 'react-aria-components'
import './estilos/index.css'
import { clienteConsultas } from './app/datos'
import { router } from './app/router'
import { aplicarTema, leerPreferenciaTema } from './app/tema'

aplicarTema(leerPreferenciaTema())

const raiz = document.getElementById('raiz')
if (!raiz) throw new Error('Falta el elemento #raiz en index.html')

createRoot(raiz).render(
  <StrictMode>
    {/* Los anuncios de React Aria al lector de pantalla van en español aunque el navegador esté en otro idioma. */}
    <I18nProvider locale="es-CO">
      <QueryClientProvider client={clienteConsultas}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
)
