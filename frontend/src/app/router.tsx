import { createRootRoute, createRoute, createRouter, lazyRouteComponent, Outlet, redirect } from '@tanstack/react-router'
import { Marco } from './Marco'
import { ConfirmarReporte } from '../pantallas/publico/ConfirmarReporte'
import { FichaSector } from '../pantallas/publico/FichaSector'
import { Historial, NoEncontrada } from '../pantallas/publico/Pendiente'
import { PantallaMapa } from '../pantallas/publico/PantallaMapa'
import { ResumenCiudad } from '../pantallas/publico/ResumenCiudad'
import { Cumplimiento } from '../pantallas/publico/Cumplimiento'
import { Estadisticas } from '../pantallas/publico/Estadisticas'
import { Bitacora } from '../pantallas/publico/Bitacora'
import { esTipoBitacora } from '../dominio/historia'
import { Avisos } from '../pantallas/publico/Avisos'
import { BajaAvisos, ConfirmarAvisos } from '../pantallas/publico/EnlaceAvisos'
import { sesion } from '../api/sesion'
import { Ingreso } from '../pantallas/panel/Ingreso'
import { PanelMarco } from '../pantallas/panel/PanelMarco'
import { Reportes } from '../pantallas/panel/Reportes'
import { Seguridad } from '../pantallas/panel/Seguridad'
import { SegundoFactor } from '../pantallas/panel/SegundoFactor'
import { Invitacion } from '../pantallas/cuenta/Invitacion'
import { Olvide } from '../pantallas/cuenta/Olvide'
import { Restablecer } from '../pantallas/cuenta/Restablecer'
import { Solicitar } from '../pantallas/cuenta/Solicitar'
import { Verificar } from '../pantallas/cuenta/Verificar'

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
  validateSearch: (busqueda: Record<string, unknown>): { sector?: string } =>
    typeof busqueda.sector === 'string' ? { sector: busqueda.sector } : {},
  component: Cumplimiento,
})
const bitacora = createRoute({
  getParentRoute: () => publico, path: 'bitacora',
  validateSearch: (busqueda: Record<string, unknown>): { sector?: string; tipo?: import('../dominio/historia').TipoBitacora; desde?: string; hasta?: string } => ({
    ...(typeof busqueda.sector === 'string' ? { sector: busqueda.sector } : {}),
    ...(esTipoBitacora(busqueda.tipo) ? { tipo: busqueda.tipo } : {}),
    ...(typeof busqueda.desde === 'string' ? { desde: busqueda.desde } : {}),
    ...(typeof busqueda.hasta === 'string' ? { hasta: busqueda.hasta } : {}),
  }),
  component: Bitacora,
})
const estadisticas = createRoute({
  getParentRoute: () => publico, path: 'estadisticas', component: Estadisticas,
})
const avisos = createRoute({
  getParentRoute: () => publico,
  path: 'avisos',
  validateSearch: (busqueda: Record<string, unknown>): { sector?: string } =>
    typeof busqueda.sector === 'string' ? { sector: busqueda.sector } : {},
  component: Avisos,
})
const confirmarAvisos = createRoute({
  getParentRoute: () => publico, path: 'avisos/confirmar',
  validateSearch: (busqueda: Record<string, unknown>): { token?: string } =>
    typeof busqueda.token === 'string' ? { token: busqueda.token } : {},
  component: ConfirmarAvisos,
})
const bajaAvisos = createRoute({
  getParentRoute: () => publico, path: 'avisos/baja',
  validateSearch: (busqueda: Record<string, unknown>): { token?: string } =>
    typeof busqueda.token === 'string' ? { token: busqueda.token } : {},
  component: BajaAvisos,
})

// Panel del veedor y cuentas (F5). El ingreso, el alta del segundo factor y /cuenta/* van dentro del marco público;
// el panel tiene su propio marco y solo se abre con una sesión completa.
const conToken = (busqueda: Record<string, unknown>): { token?: string } =>
  typeof busqueda.token === 'string' ? { token: busqueda.token } : {}
const ingreso = createRoute({
  getParentRoute: () => publico, path: 'panel/ingreso',
  validateSearch: (busqueda: Record<string, unknown>): { motivo?: string } =>
    typeof busqueda.motivo === 'string' ? { motivo: busqueda.motivo } : {},
  beforeLoad: () => {
    if (sesion.token() === null) return
    throw redirect({ to: sesion.alcance() === 'COMPLETO' ? '/panel' : '/panel/segundo-factor', replace: true })
  },
  component: Ingreso,
})
const segundoFactor = createRoute({
  getParentRoute: () => publico, path: 'panel/segundo-factor',
  beforeLoad: () => {
    if (sesion.token() === null) throw redirect({ to: '/panel/ingreso', replace: true })
    if (sesion.alcance() === 'COMPLETO') throw redirect({ to: '/panel/seguridad', replace: true })
  },
  component: SegundoFactor,
})
const solicitar = createRoute({ getParentRoute: () => publico, path: 'cuenta/solicitar', component: Solicitar })
const verificar = createRoute({ getParentRoute: () => publico, path: 'cuenta/verificar', validateSearch: conToken, component: Verificar })
const invitacion = createRoute({ getParentRoute: () => publico, path: 'cuenta/invitacion', validateSearch: conToken, component: Invitacion })
const olvide = createRoute({ getParentRoute: () => publico, path: 'cuenta/olvide', component: Olvide })
const restablecer = createRoute({ getParentRoute: () => publico, path: 'cuenta/restablecer', validateSearch: conToken, component: Restablecer })

const panel = createRoute({
  getParentRoute: () => raiz, path: 'panel',
  beforeLoad: () => {
    if (sesion.token() === null) throw redirect({ to: '/panel/ingreso', replace: true })
    if (sesion.alcance() !== 'COMPLETO') throw redirect({ to: '/panel/segundo-factor', replace: true })
  },
  component: PanelMarco,
})
const panelReportes = createRoute({ getParentRoute: () => panel, path: '/', component: Reportes })
const panelSeguridad = createRoute({ getParentRoute: () => panel, path: 'seguridad', component: Seguridad })

// Provisional de F0–F1: tokens y contraste medidos en pantalla, fuera de la navegación ciudadana.
const muestrario = createRoute({
  getParentRoute: () => raiz, path: 'muestrario', component: lazyRouteComponent(() => import('./Muestrario'), 'Muestrario'),
})

const arbol = raiz.addChildren([
  publico.addChildren([
    mapa.addChildren([inicio, sector]),
    confirmar, historial, cumplimiento, bitacora, estadisticas, avisos, confirmarAvisos, bajaAvisos,
    ingreso, segundoFactor, solicitar, verificar, invitacion, olvide, restablecer,
  ]),
  panel.addChildren([panelReportes, panelSeguridad]),
  muestrario,
])

export const router = createRouter({ routeTree: arbol, defaultPreload: 'intent', scrollRestoration: true })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
