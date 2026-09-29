import pagina from '../publico/Pagina.module.css'

/** Secciones del panel que llegan en los siguientes PR de F5: se dice sin rodeos, sin datos de relleno. */
export function PendientePanel({ titular }: { titular: string }) {
  return (
    <div className={pagina.pagina}>
      <h1 className={pagina.titular}>{titular}</h1>
      <p className={pagina.entrada}>Esta sección del panel todavía no está construida: llega en la fase F5 del frontend.</p>
    </div>
  )
}
