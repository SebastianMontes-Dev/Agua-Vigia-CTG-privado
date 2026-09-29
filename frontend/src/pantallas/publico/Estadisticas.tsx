import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useEstadisticas } from '../../app/datos'
import { formatearNumero } from '../../dominio/formato'
import { disponibilidadEstadisticas, ordenarDias } from '../../dominio/estadisticas'
import { nombreLegible } from '../../dominio/sectores'
import estilosPagina from './Pagina.module.css'
import estilos from './Estadisticas.module.css'

interface Fila {
  clave: string
  etiqueta: string
  cantidad: number | null
}

function GraficoConTabla({ titulo, columna, medida = 'Cortes', filas, tabla, limiteMovil, ampliado, alAmpliar }: {
  titulo: string
  columna: string
  medida?: string
  filas: readonly Fila[]
  tabla: string
  limiteMovil?: number
  ampliado?: boolean
  alAmpliar?: () => void
}) {
  const maximo = Math.max(1, ...filas.map((fila) => fila.cantidad ?? 0))
  return <div className={estilos.par}>
    <figure>
      <figcaption className={estilos.subtitulo}>{titulo}</figcaption>
      <ol className={estilos.barras}>{filas.map((fila, indice) => <li key={fila.clave} className={limiteMovil !== undefined && indice >= limiteMovil && !ampliado ? estilos.filaMovilPlegada : undefined}>
        <span>{fila.etiqueta}</span>
        <div className={estilos.eje}><span style={{ width: `${(fila.cantidad ?? 0) / maximo * 100}%` }} /></div>
        <strong>{fila.cantidad === null ? 'Sin dato' : formatearNumero(fila.cantidad)}</strong>
      </li>)}</ol>
      {limiteMovil !== undefined && filas.length > limiteMovil && !ampliado && <button type="button" className={estilos.verTodos} onClick={alAmpliar}>Ver todos los barrios ({formatearNumero(filas.length)})</button>}
    </figure>
    <details className={estilos.tablaPlegable}>
      <summary>Ver como tabla</summary>
      <div className={estilos.tablaContenedor}>
        <table>
          <caption>{tabla}</caption>
          <thead><tr><th scope="col">{columna}</th><th scope="col">{medida}</th></tr></thead>
          <tbody>{filas.map((fila) => <tr key={fila.clave}><th scope="row">{fila.etiqueta}</th>
            <td>{fila.cantidad === null ? 'Sin dato' : formatearNumero(fila.cantidad)}</td></tr>)}</tbody>
        </table>
      </div>
    </details>
  </div>
}

export function Estadisticas() {
  const [mostrarTodosSectores, setMostrarTodosSectores] = useState(false)
  const consulta = useEstadisticas()
  const datos = consulta.data
  const sectores = datos?.sectoresMasAfectados ?? []
  const disponibilidad = datos ? disponibilidadEstadisticas(datos) : null
  const filasSectores = sectores.map((sector) => ({
    clave: sector.sectorId ?? '',
    etiqueta: sector.nombre && sector.nombre.trim().toLowerCase() !== 'desconocido'
      ? nombreLegible(sector.nombre) : sector.sectorId ?? 'Barrio sin identificar',
    cantidad: sector.cantidadCortes ?? null,
  }))
  const filasDias = ordenarDias(datos?.cortesPorDiaDeSemana).map(({ dia, cortes }) => ({ clave: dia, etiqueta: dia, cantidad: cortes }))

  return <div className={`${estilosPagina.pagina} ${estilos.pagina}`}>
    <header className={estilos.cabecera}>
      <h1 className={estilosPagina.titular}>Estadísticas</h1>
      <p className={estilosPagina.entrada}>Qué barrios aparecen en avisos aprobados, qué días ocurren cortes y cuánto duran.</p>
      <a href="/api/estadisticas/exportar.csv" download className={estilos.descarga}>Descargar estadísticas en CSV</a>
    </header>
    {consulta.isPending && <output className={estilos.esqueleto}>Consultando las estadísticas…<span /><span /><span /></output>}
    {consulta.isError && <p role="alert">No pudimos consultar las estadísticas. Revisa tu conexión e inténtalo otra vez. <button type="button" onClick={() => consulta.refetch()}>Reintentar</button></p>}
    {disponibilidad?.vacioGeneral && <p className={estilos.vacio}>Todavía no hay datos registrados para resumir.</p>}
    {datos && disponibilidad && <>
      <p className={estilos.duracion}>Duración media de los cortes: <strong>{disponibilidad.duracionMedible === null ? 'Aún no hay cortes cerrados para medir' : `${formatearNumero(disponibilidad.duracionMedible, 1)} horas`}</strong></p>
      <section className={estilos.seccion} aria-labelledby="titulo-sectores">
        <h2 id="titulo-sectores">Barrios que más aparecen en avisos de corte</h2>
        <p className={estilos.explicacionAvisos}>Veces que el barrio aparece en un boletín de Acuacar aprobado. No son cortes con duración medida: esos están en <Link to="/cumplimiento">Cumplimiento</Link>.</p>
        {sectores.length === 0
          ? <p className={estilos.vacio}>Todavía no hay boletines de Acuacar aprobados que mencionen barrios.</p>
          : <GraficoConTabla titulo="Barrios" columna="Barrio" medida="Avisos" filas={filasSectores} tabla="Avisos de corte aprobados por barrio" limiteMovil={3} ampliado={mostrarTodosSectores} alAmpliar={() => setMostrarTodosSectores(true)} />}
      </section>
      <section className={estilos.seccion} aria-labelledby="titulo-dias">
        <h2 id="titulo-dias">Cortes por día de la semana</h2>
        {disponibilidad.totalCortes === 0
          ? <p className={estilos.vacio}>Todavía no hay cortes registrados.</p>
          : <GraficoConTabla titulo="Días" columna="Día" filas={filasDias} tabla="Cortes registrados de lunes a domingo" />}
      </section>
    </>}
  </div>
}
