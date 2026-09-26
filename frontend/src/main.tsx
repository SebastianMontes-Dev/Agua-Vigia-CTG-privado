import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './estilos/index.css'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { router } from './app/rutas'
import { FalloCiudadano } from './api/ciudadano'
import { aplicarTema, leerPreferenciaTema } from './app/tema'

aplicarTema(leerPreferenciaTema())

const raiz = document.getElementById('raiz')
if (!raiz) throw new Error('Falta el elemento #raiz en index.html')

createRoot(raiz).render(
  <StrictMode>
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: (intento, error) => intento < 2 && error instanceof FalloCiudadano && error.dato.reintentable && error.dato.estado !== 429, refetchOnWindowFocus: false, refetchOnReconnect: false, staleTime: 30_000 }, mutations: { retry: false } } })}><RouterProvider router={router} /></QueryClientProvider>
  </StrictMode>,
)
