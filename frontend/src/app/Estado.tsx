import type { CSSProperties } from 'react'
import { presentarEstado, type EstadoServicio } from '../dominio/estados'
import conServicio from '../iconos/estado-con-servicio.svg?raw'
import sinServicio from '../iconos/estado-sin-servicio.svg?raw'
import presion from '../iconos/estado-presion-baja.svg?raw'
import programado from '../iconos/estado-corte-programado.svg?raw'
import sinDatos from '../iconos/estado-sin-datos.svg?raw'
const iconos = { CON_SERVICIO: conServicio, SIN_SERVICIO: sinServicio, PRESION_BAJA: presion, CORTE_PROGRAMADO: programado, SIN_DATOS: sinDatos }
export function Estado({ estado }: { estado: EstadoServicio | null | undefined }) {
  const presentacion = presentarEstado(estado)
  return <span className={`estado ${estado == null ? 'sin-datos' : ''}`} style={{ '--color-estado': presentacion.variableColor ? `var(${presentacion.variableColor})` : 'var(--tinta-2)' } as CSSProperties}>
    <span className="glifo" aria-hidden="true" dangerouslySetInnerHTML={{ __html: iconos[presentacion.clave] }} />{presentacion.texto}
  </span>
}
