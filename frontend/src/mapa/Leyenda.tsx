import { GlifoEstado } from '../componentes/GlifoEstado'
import { ORDEN_ESTADOS, presentarEstado } from '../dominio/estados'
import estilos from './Mapa.module.css'

export function Leyenda() {
  return (
    <ul className={estilos.leyenda} aria-label="Leyenda del mapa">
      {[...ORDEN_ESTADOS, null].map((estado) => (
        <li key={estado ?? 'SIN_DATOS'}>
          <GlifoEstado estado={estado} tamano={16} />
          {presentarEstado(estado).texto}
        </li>
      ))}
    </ul>
  )
}
