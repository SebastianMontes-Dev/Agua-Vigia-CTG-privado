import { Link } from '@tanstack/react-router'
import estilos from './Pagina.module.css'

interface Props {
  titular: string
  fase: string
}

/** Destinos de la navegación que llegan en otra fase del plan: se dice sin rodeos y se ofrece el mapa. */
export function Pendiente({ titular, fase }: Props) {
  return (
    <div className={estilos.pagina}>
      <h1 className={estilos.titular}>{titular}</h1>
      <p className={estilos.entrada}>
        Esta página todavía no está construida: llega en la {fase} del frontend. Mientras tanto, el mapa muestra el
        estado del agua de cada barrio y el histórico de sus cortes.
      </p>
      <Link to="/" className={estilos.enlace}>Ir al mapa</Link>
    </div>
  )
}

export function Historial() {
  return (
    <div className={estilos.pagina}>
      <h1 className={estilos.titular}>Historial</h1>
      <p className={estilos.entrada}>Lo que pasó con el agua en Cartagena, contado de tres maneras.</p>
      <ul className={estilos.destinos}>
        <li><Link to="/cumplimiento" className={estilos.destino}>Cumplimiento<span>Lo prometido y lo que duró cada corte</span></Link></li>
        <li><Link to="/bitacora" className={estilos.destino}>Bitácora<span>Cada cambio de estado, con su fuente</span></Link></li>
        <li><Link to="/estadisticas" className={estilos.destino}>Estadísticas<span>Barrios más afectados y días con más cortes</span></Link></li>
      </ul>
    </div>
  )
}

export function NoEncontrada() {
  return (
    <div className={estilos.pagina}>
      <h1 className={estilos.titular}>No encontramos esta página</h1>
      <p className={estilos.entrada}>El enlace no lleva a ninguna parte de AguaVigía.</p>
      <Link to="/" className={estilos.enlace}>Ir al mapa</Link>
    </div>
  )
}
