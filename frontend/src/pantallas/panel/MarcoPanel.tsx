import { Link, Outlet, useLocation } from '@tanstack/react-router'
import { Redirigir } from '../../app/Redirigir'
import { useState } from 'react'
import { cerrarSesion } from '../../api/panel'
import { useAlcance, useCuentaActual, useSesionPerdida } from '../../app/panel'
import { SelectorTema } from '../../app/SelectorTema'
import { permisosDe, seccionActiva, seccionesPermitidas } from '../../dominio/permisos'
import estilos from './MarcoPanel.module.css'

/**
 * Guía §4.2: el panel tiene su propia navegación, solo con las secciones que los permisos permiten. Sin sesión
 * lleva al ingreso; con la sesión restringida de un ADMIN sin TOTP, al alta del segundo factor.
 */
export function MarcoPanel() {
  const alcance = useAlcance()
  const perdida = useSesionPerdida(alcance)
  const [saliendo, setSaliendo] = useState(false)
  const cuenta = useCuentaActual(alcance === 'COMPLETO')
  const { pathname } = useLocation()

  if (!alcance) return <Redirigir destino={{ to: '/panel/ingreso', search: saliendo ? { motivo: 'cerrada' } : perdida ? { motivo: 'vencida' } : {} }} />
  if (alcance === 'ALTA_SEGUNDO_FACTOR') return <Redirigir destino={{ to: '/panel/segundo-factor' }} />

  // Al borrar el token, la guarda de arriba lleva al ingreso con el motivo correcto.
  function salir() {
    setSaliendo(true)
    void cerrarSesion()
  }

  const secciones = seccionesPermitidas(permisosDe(cuenta.data?.permisosEfectivos))

  return (
    <div className={estilos.marco}>
      <a href="#contenido-panel" className={estilos.saltar}>Saltar al contenido</a>
      <header className={estilos.cabecera}>
        <Link to="/panel" className={estilos.marca} aria-label="AguaVigía, panel del veedor: ir a la moderación">
          AguaVigía <span className={estilos.rotulo}>Panel</span>
        </Link>
        {cuenta.data && (
          <p className={estilos.cuenta}>
            <span className={estilos.nombre}>{cuenta.data.nombre ?? cuenta.data.correo}</span>
            {cuenta.data.nombre && cuenta.data.correo && <span className={estilos.correo}>{cuenta.data.correo}</span>}
          </p>
        )}
        <div className={estilos.tema}><SelectorTema /></div>
        <button type="button" className={estilos.salir} onClick={salir} disabled={saliendo}>
          {saliendo ? 'Cerrando…' : 'Cerrar sesión'}
        </button>
      </header>
      {cuenta.data && (
        <nav aria-label="Secciones del panel" className={estilos.secciones}>
          {secciones.map((seccion) => (
            <Link
              key={seccion.to}
              to={seccion.to}
              className={estilos.seccion}
              aria-current={seccionActiva(pathname, seccion.to) ? 'page' : undefined}
            >
              {seccion.texto}
            </Link>
          ))}
          <Link to="/" className={`${estilos.seccion} ${estilos.volver}`}>Volver al mapa</Link>
        </nav>
      )}
      <main id="contenido-panel" className={estilos.principal} tabIndex={-1}>
        {cuenta.isPending && <output className={estilos.estado}>Consultando tu cuenta…</output>}
        {cuenta.isError && (
          <p className={estilos.estado} role="alert">
            No pudimos consultar tu cuenta. Revisa tu conexión e inténtalo otra vez.{' '}
            <button type="button" className={estilos.reintentar} onClick={() => void cuenta.refetch()}>Reintentar</button>
          </p>
        )}
        {cuenta.data && <Outlet />}
      </main>
    </div>
  )
}
