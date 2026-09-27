import { useId } from 'react'
import conServicio from '../iconos/estado-con-servicio.svg?raw'
import corteProgramado from '../iconos/estado-corte-programado.svg?raw'
import presionBaja from '../iconos/estado-presion-baja.svg?raw'
import sinDatos from '../iconos/estado-sin-datos.svg?raw'
import sinServicio from '../iconos/estado-sin-servicio.svg?raw'
import type { EstadoServicio } from '../dominio/estados'
import estilos from './GlifoEstado.module.css'

const SVG: Record<EstadoServicio | 'SIN_DATOS', string> = {
  CON_SERVICIO: conServicio,
  SIN_SERVICIO: sinServicio,
  PRESION_BAJA: presionBaja,
  CORTE_PROGRAMADO: corteProgramado,
  SIN_DATOS: sinDatos,
}

const VARIABLE: Record<EstadoServicio | 'SIN_DATOS', string> = {
  CON_SERVICIO: 'var(--estado-con-servicio)',
  SIN_SERVICIO: 'var(--estado-sin-servicio)',
  PRESION_BAJA: 'var(--estado-presion-baja)',
  CORTE_PROGRAMADO: 'var(--estado-corte-programado)',
  SIN_DATOS: 'var(--tinta-2)',
}

// Los SVG de F1 son la única fuente de cada glifo. Sus máscaras llevan id fijo: repetido en la página,
// el navegador tomaría la del primero, que puede estar oculto. Se prefijan por instancia.
function aislarIds(svg: string, prefijo: string): string {
  return svg.replace(/id="([^"]+)"/g, `id="${prefijo}$1"`).replace(/url\(#([^)]+)\)/g, `url(#${prefijo}$1)`)
}

interface Props {
  estado: EstadoServicio | null | undefined
  tamano?: 16 | 20 | 24
}

/** Decorativo: el texto del estado siempre va al lado (DESIGN.md §2, el color nunca va solo). */
export function GlifoEstado({ estado, tamano = 20 }: Props) {
  const clave = estado ?? 'SIN_DATOS'
  const prefijo = useId().replace(/[^a-zA-Z0-9_-]/g, '')
  return (
    <span
      aria-hidden="true"
      className={estilos.glifo}
      style={{ color: VARIABLE[clave], width: tamano, height: tamano }}
      dangerouslySetInnerHTML={{ __html: aislarIds(SVG[clave], prefijo) }}
    />
  )
}
