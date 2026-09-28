import { createRootRoute, createRoute, createRouter, lazyRouteComponent, Outlet } from '@tanstack/react-router'
import { Marco } from './Marco'
import { ConfirmarReporte } from '../pantallas/publico/ConfirmarReporte'
import { FichaSector } from '../pantallas/publico/FichaSector'
import { Historial, NoEncontrada, Pendiente } from '../pantallas/publico/Pendiente'
import { PantallaMapa } from '../pantallas/publico/PantallaMapa'
import { ResumenCiudad } from '../pantallas/publico/ResumenCiudad'
import { Cumplimiento } from '../pantallas/publico/Cumplimiento'

const raiz = createRootRoute({ component: Outlet, notFoundComponent: NoEncontrada })

const publico = createRoute({ getParentRoute: () => raiz, id: 'publico', component: Marco })

// Ruta sin segmento: el mapa se monta una vez para la ciudad y para cada barrio.
const mapa = createRoute({ getParentRoute: () => publico, id: 'mapa', component: PantallaMapa })
const inicio = createRoute({ getParentRoute: () => mapa, path: '/', component: ResumenCiudad })
const sector = createRoute({ getParentRoute: () => mapa, path: 'sectores/$id', component: FichaSector })

const confirmar = createRoute({ getParentRoute: () => publico, path: 'confirmar/$id', component: ConfirmarReporte })

const historial = createRoute({ getParentRoute: () => publico, path: 'historial', component: Historial })
const cumplimiento = createRoute({
  getParentRoute: () => publico, path: 'cumplimiento',
  validateSearch: (busqueda: Record<string, unknown>): { sector?: string; desde?: string; hasta?: string } => ({
    ...(typeof busqueda.sector === 'string' ? { sector: busqueda.sector } : {}),
    ...(typeof busqueda.desde === 'string' ? { desde: busqueda.desde } : {}),
    ...(typeof busqueda.hasta === 'string' ? { hasta: busqueda.hasta } : {}),
  }),
  component: Cumplimiento,
})
const bitacora = createRoute({
  getParentRoute: () => publico, path: 'bitacora', component: () => <Pendiente titular="Bitácora" fase="fase F3" />,
})
const estadisticas = createRoute({
  getParentRoute: () => publico, path: 'estadisticas', component: () => <Pendiente titular="Estadísticas" fase="fase F3" />,
})
const avisos = createRoute({
  getParentRoute: () => publico,
  path: 'avisos',
  validateSearch: (busqueda: Record<string, unknown>): { sector?: string } =>
    typeof busqueda.sector === 'string' ? { sector: busqueda.sector } : {},
  component: () => <Pendiente titular="Recibe avisos de tus barrios" fase="fase F4" />,
})

// Provisional de F0–F1: tokens y contraste medidos en pantalla, fuera de la navegación ciudadana.
const muestrario = createRoute({
  getParentRoute: () => raiz, path: 'muestrario', component: lazyRouteComponent(() => import('./Muestrario'), 'Muestrario'),
})

const arbol = raiz.addChildren([
  publico.addChildren([
    mapa.addChildren([inicio, sector]),
    confirmar, historial, cumplimiento, bitacora, estadisticas, avisos,
  ]),
  muestrario,
])

export const router = createRouter({ routeTree: arbol, defaultPreload: 'intent', scrollRestoration: true })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
