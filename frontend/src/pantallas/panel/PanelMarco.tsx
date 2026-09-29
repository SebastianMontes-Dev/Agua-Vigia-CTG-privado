import { Link, Outlet, useLocation, useNavigate } from '@tanstack/react-router'
import { useEffect, useRef } from 'react'
import { Button, Dialog, DialogTrigger, Popover } from 'react-aria-components'
import { cerrarSesion, type Permiso } from '../../api/panel'
import { SelectorTema } from '../../app/SelectorTema'
import { useCuenta, useToken } from '../../app/sesion-panel'
import marco from '../../app/Marco.module.css'
import estilos from './PanelMarco.module.css'

interface Seccion { to: '/panel' | '/panel/seguridad'; texto: string; permiso: Permiso | null }

// La navegación sale de `permisos[]`, no del rol (docs/api/cuentas-y-sesion.md). Las listas se ven con VER_PANEL;
// lo que cambia algo se decide dentro de cada pantalla.
export const SECCIONES: readonly Seccion[] = [
  { to: '/panel', texto: 'Reportes', permiso: 'VER_PANEL' },
  { to: '/panel/seguridad', texto: 'Mi seguridad', permiso: null },
]

export function seccionesPermitidas(permisos: readonly string[] | undefined): Seccion[] {
  return SECCIONES.filter((seccion) => seccion.permiso === null || (permisos?.includes(seccion.permiso) ?? false))
}

export function seccionActiva(ruta: string, destino: string): boolean {
  return destino === '/panel' ? ruta === '/panel' || ruta === '/panel/' : ruta.startsWith(destino)
}

const ROL: Record<string, string> = { ADMIN: 'Administrador', VEEDOR: 'Veedor', OBSERVADOR: 'Observador' }

export function PanelMarco() {
  const { pathname } = useLocation()
  const navegar = useNavigate()
  const token = useToken()
  const cuenta = useCuenta()
  const teniaSesion = useRef(token !== null)
  const salioPorSuCuenta = useRef(false)

  // Un 401 limpia el token desde el cliente: la persona vuelve al ingreso sabiendo por qué (guía §6.2).
  useEffect(() => {
    if (token !== null) { teniaSesion.current = true; return }
    if (teniaSesion.current) {
      void navegar({ to: '/panel/ingreso', search: salioPorSuCuenta.current ? {} : { motivo: 'terminada' }, replace: true })
    }
  }, [token, navegar])

  function salir() {
    salioPorSuCuenta.current = true
    void cerrarSesion()
  }

  const secciones = seccionesPermitidas(cuenta.data?.permisosEfectivos)
  const enlaces = (clase: string | undefined) => secciones.map((seccion) => (
    <Link key={seccion.to} to={seccion.to} className={clase}
      aria-current={seccionActiva(pathname, seccion.to) ? 'page' : undefined}>{seccion.texto}</Link>
  ))

  return (
    <div className={marco.marco}>
      <a href="#contenido" className={marco.saltar}>Saltar al contenido</a>
      <header className={marco.cabecera}>
        <Link to="/panel" className={marco.marca} aria-label="AguaVigía, panel del veedor">
          AguaVigía <span className={marco.ciudad}>Panel</span>
        </Link>
        <nav aria-label="Secciones del panel" className={marco.navegacion}>{enlaces(marco.destino)}</nav>
        <div className={`${marco.temaEscritorio} ${estilos.usuario}`}>
          {cuenta.data && <span className={estilos.quien}><strong>{cuenta.data.nombre}</strong> · {ROL[cuenta.data.rol ?? ''] ?? cuenta.data.rol}</span>}
          <Link to="/" className={estilos.enlace}>Volver al mapa</Link>
          <button type="button" className={estilos.enlace} onClick={salir}>Cerrar sesión</button>
          <SelectorTema />
        </div>
        <DialogTrigger>
          <Button className={marco.menu}>Menú</Button>
          <Popover className={marco.menuPopover} placement="bottom end">
            <Dialog className={marco.menuDialogo} aria-label="Menú del panel">
              {cuenta.data && <p className={estilos.quien}><strong>{cuenta.data.nombre}</strong> · {ROL[cuenta.data.rol ?? ''] ?? cuenta.data.rol}</p>}
              <nav aria-label="Secciones" className={marco.menuEnlaces}>
                {enlaces(marco.menuDestino)}
                <Link to="/" className={marco.menuDestino}>Volver al mapa</Link>
              </nav>
              <button type="button" className={estilos.enlace} onClick={salir}>Cerrar sesión</button>
              <SelectorTema />
            </Dialog>
          </Popover>
        </DialogTrigger>
      </header>
      <main id="contenido" className={marco.principal} tabIndex={-1}>
        {cuenta.isPending ? (
          <output className={estilos.estado}>Consultando tu cuenta…</output>
        ) : cuenta.isError ? (
          <div className={estilos.estado}>
            <p role="alert">{cuenta.error.estado === 403
              ? 'Tu cuenta ya no tiene acceso a esta parte del panel.'
              : 'No pudimos consultar tu cuenta. Revisa tu conexión e inténtalo otra vez.'}</p>
            <button type="button" className={estilos.enlace} onClick={() => void cuenta.refetch()}>Volver a intentar</button>
          </div>
        ) : <Outlet />}
      </main>
    </div>
  )
}
