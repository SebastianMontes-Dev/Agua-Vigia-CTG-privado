import { createRootRoute, createRoute, createRouter, Outlet } from '@tanstack/react-router'
import { Cabecera, Ciudadano } from './Ciudadano'
import { lazy, Suspense } from 'react'
const Confirmar = lazy(() => import('./Reporte').then((modulo) => ({ default: modulo.Confirmar })))
const Muestrario = lazy(() => import('./Muestrario').then((modulo) => ({ default: modulo.Muestrario })))
const raiz = createRootRoute({ component: () => <><Cabecera /><Suspense fallback={<output className="aviso">Preparando la página…</output>}><Outlet /></Suspense></>, notFoundComponent: () => <main id="contenido" tabIndex={-1} className="confirmar"><h1>Página no disponible</h1><p>Esta sección todavía está en construcción.</p><a href="/">Volver al mapa</a></main> })
const mapa = createRoute({ getParentRoute: () => raiz, path: '/', component: Ciudadano })
const sector = createRoute({ getParentRoute: () => raiz, path: '/sectores/$id', component: Ciudadano })
const confirmar = createRoute({ getParentRoute: () => raiz, path: '/confirmar/$id', component: () => <Confirmar id={confirmar.useParams().id} /> })
const muestrario = createRoute({ getParentRoute: () => raiz, path: '/muestrario', component: Muestrario })
export const router = createRouter({ routeTree: raiz.addChildren([mapa, sector, confirmar, muestrario]), defaultPreload: false })
declare module '@tanstack/react-router' { interface Register { router: typeof router } }
