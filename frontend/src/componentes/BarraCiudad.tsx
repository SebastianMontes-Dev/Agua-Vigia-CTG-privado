import { ORDEN_CONTEO, type ClaveConteo } from '../dominio/sectores'
import estilos from './BarraCiudad.module.css'

const COLOR: Record<ClaveConteo, string | null> = {
  SIN_SERVICIO: 'var(--estado-sin-servicio)',
  CORTE_PROGRAMADO: 'var(--estado-corte-programado)',
  PRESION_BAJA: 'var(--estado-presion-baja)',
  CON_SERVICIO: 'var(--estado-con-servicio)',
  SIN_DATOS: null,
}

/** Decorativa: los mismos conteos van escritos debajo, con su glifo y su palabra. */
export function BarraCiudad({ conteo }: { conteo: Record<ClaveConteo, number> }) {
  const total = ORDEN_CONTEO.reduce((suma, clave) => suma + conteo[clave], 0)
  if (total === 0) return null
  return (
    <div className={estilos.barra} aria-hidden="true">
      {ORDEN_CONTEO.filter((clave) => conteo[clave] > 0).map((clave) => (
        <span
          key={clave}
          className={COLOR[clave] ? estilos.tramo : `${estilos.tramo} ${estilos.trama}`}
          style={{ flexGrow: conteo[clave], background: COLOR[clave] ?? undefined }}
        />
      ))}
    </div>
  )
}
