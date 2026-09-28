import { useNavigate, useSearch } from '@tanstack/react-router'
import { useIndiceCumplimiento, useListado, useSerieCumplimiento } from '../../app/datos'
import { SelectorBarrio } from '../../componentes/SelectorBarrio'
import { conclusionCumplimiento, duracionAcumulada, indiceCompleto, limiteSerieCartagena, mesEnPalabras, ordenarSerie, rangoSerieValido, type Indice, type PuntoSerie } from '../../dominio/cumplimiento'
import { formatearNumero, formatearPorcentaje } from '../../dominio/formato'
import estilosPagina from './Pagina.module.css'
import estilos from './Cumplimiento.module.css'

function horas(segundos: number): string { return duracionAcumulada(segundos) }

function Comparacion({ indice }: { indice: Indice }) {
  if (!indiceCompleto(indice)) return <p role="alert">No pudimos leer la comparación de los cortes. Reintenta la consulta.</p>
  const escala = Math.max(indice.duracionPrometidaSegundos, indice.duracionRealSegundos, 1)
  const prometido = indice.duracionPrometidaSegundos / escala * 100
  const real = indice.duracionRealSegundos / escala * 100
  const exceso = Math.max(0, real - prometido)

  return (
    <section className={estilos.comparacion} aria-label="Comparación entre lo prometido y lo real">
      <p className={estilos.conclusion}>{conclusionCumplimiento(indice)}</p>
      <div className={estilos.cifras}>
        <p><span>Prometido</span><strong>{horas(indice.duracionPrometidaSegundos)}</strong></p>
        <p><span>Real</span><strong>{horas(indice.duracionRealSegundos)}</strong></p>
        <p><span>Diferencia</span><strong>{indice.desviacionSegundos === 0 ? 'Sin diferencia' : `${horas(Math.abs(indice.desviacionSegundos))} ${indice.desviacionSegundos > 0 ? 'más' : 'menos'}`}</strong></p>
        <p><span>Índice</span><strong>{formatearPorcentaje(indice.porcentajeCumplimiento)}</strong></p>
      </div>
      <div className={estilos.graficoComparacion} aria-hidden="true">
        <p>Prometido</p><div className={estilos.eje}><span className={estilos.prometido} style={{ width: `${prometido}%` }} /></div>
        <p>Real</p><div className={estilos.eje}><span className={estilos.real} style={{ width: `${Math.min(real, prometido)}%` }} />
          {exceso > 0 && <span className={estilos.exceso} style={{ left: `${prometido}%`, width: `${exceso}%` }} />}</div>
      </div>
      <p className={estilos.regla}>Solo cuentan los cortes cerrados registrados. El agregado suma sus duraciones; no promedia porcentajes.</p>
    </section>
  )
}

function Serie({ puntos }: { puntos: readonly PuntoSerie[] }) {
  const serie = ordenarSerie(puntos)
  if (!serie.length) return <p className={estilos.vacio}>No hay cortes cerrados en la serie para esas fechas.</p>
  return (
    <div className={estilos.serieContenido}>
      <figure className={estilos.figura}>
        <figcaption>Índice de cumplimiento por mes</figcaption>
        <ol className={estilos.barras} aria-hidden="true">
          {serie.map((punto) => <li key={punto.periodo}>
            <span>{mesEnPalabras(punto.periodo)}</span>
            <div className={estilos.barraEje}><span style={{ width: `${Math.max(0, Math.min(100, punto.porcentajeCumplimiento ?? 0))}%` }} /></div>
            <strong>{punto.porcentajeCumplimiento === undefined ? 'Sin dato' : formatearPorcentaje(punto.porcentajeCumplimiento)}</strong>
            <small>{punto.cantidadCortes === undefined ? 'Sin dato de cortes' : `sobre ${formatearNumero(punto.cantidadCortes)} cortes`}</small>
          </li>)}
        </ol>
      </figure>
      <div className={estilos.tablaContenedor}>
        <table>
          <caption>Datos de la serie mensual de cumplimiento</caption>
          <thead><tr><th scope="col">Mes</th><th scope="col">Índice</th><th scope="col">Cortes</th><th scope="col">Prometido</th><th scope="col">Real</th></tr></thead>
          <tbody>{serie.map((punto) => <tr key={punto.periodo}>
            <th scope="row">{mesEnPalabras(punto.periodo)}</th>
            <td>{punto.porcentajeCumplimiento === undefined ? 'Sin dato' : formatearPorcentaje(punto.porcentajeCumplimiento)}</td>
            <td>{punto.cantidadCortes === undefined ? 'Sin dato' : formatearNumero(punto.cantidadCortes)}</td>
            <td>{punto.duracionPrometidaSegundos === undefined ? 'Sin dato' : horas(punto.duracionPrometidaSegundos)}</td>
            <td>{punto.duracionRealSegundos === undefined ? 'Sin dato' : horas(punto.duracionRealSegundos)}</td>
          </tr>)}</tbody>
        </table>
      </div>
    </div>
  )
}

export function Cumplimiento() {
  const buscar = useSearch({ from: '/publico/cumplimiento' })
  const navegar = useNavigate()
  const { sectores, lectura, reintentar: reintentarListado } = useListado()
  const rangoValido = rangoSerieValido(buscar.desde, buscar.hasta)
  const filtros = {
    sectorId: buscar.sector,
    desde: buscar.desde ? limiteSerieCartagena(buscar.desde) ?? undefined : undefined,
    hasta: buscar.hasta ? limiteSerieCartagena(buscar.hasta, true) ?? undefined : undefined,
  }
  const indice = useIndiceCumplimiento(buscar.sector)
  const serie = useSerieCumplimiento(filtros, rangoValido)
  const errorIndice = indice.error as { estado?: number } | null
  const actualizar = (cambio: Partial<typeof buscar>) => navegar({ to: '/cumplimiento', search: { ...buscar, ...cambio }, replace: true })
  const parametros = new URLSearchParams()
  if (filtros.sectorId) parametros.set('sectorId', filtros.sectorId)
  if (filtros.desde) parametros.set('desde', filtros.desde)
  if (filtros.hasta) parametros.set('hasta', filtros.hasta)
  const csv = `/api/cumplimiento/serie.csv${parametros.size ? `?${parametros.toString()}` : ''}`

  return (
    <main className={`${estilosPagina.pagina} ${estilos.pagina}`}>
      <header className={estilos.cabecera}>
        <h1 className={estilosPagina.titular}>Lo prometido y lo que duró</h1>
        <p className={estilosPagina.entrada}>Comparamos las horas anunciadas para los cortes de agua con las horas que duraron.</p>
      </header>
      <div className={estilos.selector}><SelectorBarrio sectores={sectores} valor={buscar.sector} alCambiar={(sector) => actualizar({ sector })} />
        {lectura.error && !sectores.length && <p role="alert">No pudimos cargar los barrios. <button type="button" onClick={reintentarListado}>Reintentar</button></p>}
      </div>
      {indice.isPending && <output className={estilos.esqueleto}>Consultando el cumplimiento…<span /><span /><span /></output>}
      {indice.isError && errorIndice?.estado === 400 && <p className={estilos.vacio}>Aún no hay cortes cerrados para medir. Un corte abierto todavía no se puede medir.</p>}
      {indice.isError && errorIndice?.estado !== 400 && <p role="alert">No pudimos consultar el cumplimiento. Revisa tu conexión e inténtalo otra vez. <button type="button" onClick={() => indice.refetch()}>Reintentar</button></p>}
      {indice.data && <Comparacion indice={indice.data} />}
      <section className={estilos.seccionSerie} aria-labelledby="titulo-serie">
        <header className={estilos.cabeceraSerie}><div><h2 id="titulo-serie">Mes a mes</h2>
          <p>Las fechas de abajo filtran solo la serie y el CSV; la comparación de arriba siempre muestra todos los cortes cerrados.</p></div>
          <a href={csv} download>Descargar serie en CSV</a></header>
        <div className={estilos.fechas}>
          <label>Desde (día de Cartagena)<input type="date" value={buscar.desde ?? ''} onChange={(e) => actualizar({ desde: e.target.value || undefined })} /></label>
          <label>Hasta (inclusive, día de Cartagena)<input type="date" value={buscar.hasta ?? ''} onChange={(e) => actualizar({ hasta: e.target.value || undefined })} /></label>
          {!rangoValido && <p role="alert">Revisa las fechas: la final debe ser igual o posterior a la inicial.</p>}
        </div>
        {rangoValido && serie.isPending && <output className={estilos.esqueleto}>Consultando la serie mensual…<span /><span /></output>}
        {serie.isError && <p role="alert">No pudimos consultar la serie mensual. <button type="button" onClick={() => serie.refetch()}>Reintentar</button></p>}
        {serie.data && <Serie puntos={serie.data} />}
      </section>
    </main>
  )
}
