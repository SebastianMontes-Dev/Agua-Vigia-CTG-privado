import { Link, useNavigate } from '@tanstack/react-router'
import { cerrarSesion } from '../../api/panel'
import { AltaSegundoFactor } from './AltaSegundoFactor'
import estilos from './Formulario.module.css'

/** Primer ingreso de un ADMIN: con este alcance el token solo sirve aquí, así que no hay panel hasta activar el segundo factor. */
export function SegundoFactor() {
  const navegar = useNavigate()
  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Panel del veedor · primer ingreso</p>
        <h1 className={estilos.titular}>Activa tu segundo factor</h1>
        <p className={estilos.entrada}>Las cuentas de administrador entran con la clave y un código de tu teléfono. Es un solo paso y no vuelves a escribir tu clave.</p>
      </header>
      <AltaSegundoFactor automatico exigeCodigoActual={false} alActivar={() => void navegar({ to: '/panel', replace: true })} />
      <nav className={estilos.enlaces} aria-label="Otras opciones">
        <button type="button" className={estilos.enlace}
          onClick={() => void cerrarSesion().then(() => navegar({ to: '/panel/ingreso', replace: true }))}>Salir sin activarlo</button>
        <Link to="/" className={estilos.enlace}>Volver al mapa</Link>
      </nav>
    </div>
  )
}
