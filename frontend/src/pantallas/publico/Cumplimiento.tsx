import { useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useIndiceCumplimiento, useListado, useSerieCumplimiento } from '../../app/datos'
import { SelectorBarrio } from '../../componentes/SelectorBarrio'
import {
  conclusionCumplimiento,
  diferenciaEnPalabras,
  diferenciaMensualPorCorte,
  duracionCorta,
  indiceCompleto,
  mesEnPalabras,
  ordenarSerie,
  periodoEnPalabras,
  resumirCumplimiento,
  significadoIndice,
  titularVeredicto,
  type Indice,
  type PuntoSerie,
  type ResumenCumplimiento,
} from '../../dominio/cumplimiento'
import { formatearNumero, formatearPorcentaje } from '../../dominio/formato'
import { nombreLegible } from '../../dominio/sectores'
import estilosPagina from './Pagina.module.css'
import estilos from './Cumplimiento.module.css'

const MESES_VISIBLES = 6

function Respuesta({ resumen }: { resumen: ResumenCumplimiento }) {
  return (
    <section className={estilos.respuesta} aria-label="Promedio por corte">
      <dl className={estilos.cifras}>
        <div><dt>Se anuncia</dt><dd>{duracionCorta(resumen.prometidoPorCorte)}</dd></div>
        <div><dt>Dura</dt><dd>{duracionCorta(resumen.realPorCorte)}</dd></div>
        <div><dt>Diferencia</dt><dd>{diferenciaEnPalabras(resumen.diferenciaPorCorte)}</dd></div>
      </dl>
      <p className={estilos.nota}>
        Promedio por corte. <strong>Índice {formatearPorcentaje(resumen.porcentaje)}</strong>: {significadoIndice(resumen.porcentaje)}.
      </p>
      <details className={estilos.comoSeMide}>
        <summary>Cómo se mide</summary>
        <p>
          Solo cuentan los cortes cerrados. Se suma lo que se anunció y lo que duró cada uno, y se divide entre la cantidad
          de cortes; el índice compara esas dos sumas y no pasa de 100 %.
        </p>
      </details>
    </section>
  )
}

/** Sin la serie no hay conteo ni promedio por corte: se dice el total sin inventar lo demás. */
function RespuestaSinSerie({ indice }: { indice: Indice }) {
  if (!indiceCompleto(indice)) return <p role="alert">No pudimos leer la comparación de los cortes. Reintenta la consulta.</p>
  return (
    <section className={estilos.respuesta} aria-label="Total de los cortes cerrados">
      <p className={estilos.nota}>{conclusionCumplimiento(indice)}</p>
      <p className={estilos.nota}><strong>Índice {formatearPorcentaje(indice.porcentajeCumplimiento)}</strong>: {significadoIndice(indice.porcentajeCumplimiento)}.</p>
    </section>
  )
}

function MesAMes({ puntos, csv }: { puntos: readonly PuntoSerie[]; csv: string }) {
  const [verTodos, setVerTodos] = useState(false)
  const serie = ordenarSerie(puntos)
  const visibles = verTodos ? serie : serie.slice(-MESES_VISIBLES)
  const maximo = Math.max(60, ...serie.map((punto) => Math.abs(diferenciaMensualPorCorte(punto) ?? 0)))

  return (
    <section className={estilos.evidencia} aria-labelledby="titulo-serie">
      <header className={estilos.cabeceraSerie}>
        <h2 id="titulo-serie">Mes a mes</h2>
        <p>Diferencia promedio por corte</p>
      </header>
      <ol className={estilos.meses}>
        {visibles.map((punto) => {
          const diferencia = diferenciaMensualPorCorte(punto)
          const ancho = diferencia === null ? 0 : Math.abs(diferencia) / maximo * 50
          return (
            <li key={punto.periodo}>
              <span className={estilos.mes}>{mesEnPalabras(punto.periodo)}</span>
              <span className={estilos.eje} aria-hidden="true">
                {diferencia !== null && diferencia !== 0 && (
                  <span className={diferencia > 0 ? estilos.mas : estilos.menos} style={{ width: `${ancho}%` }} />
                )}
              </span>
              <strong className={estilos.valor}>{diferencia === null ? 'Sin dato' : diferenciaEnPalabras(diferencia)}</strong>
              <span className={estilos.cantidad}>
                {punto.cantidadCortes === undefined ? 'sin conteo' : `${formatearNumero(punto.cantidadCortes)} ${punto.cantidadCortes === 1 ? 'corte' : 'cortes'}`}
              </span>
            </li>
          )
        })}
      </ol>
      {serie.length > MESES_VISIBLES && !verTodos && (
        <button type="button" className={estilos.accion} onClick={() => setVerTodos(true)}>
          Ver los {formatearNumero(serie.length)} meses
        </button>
      )}
      <details className={estilos.datos}>
        <summary>Ver datos</summary>
        <div className={estilos.tablaContenedor}>
          <table>
            <caption>Datos de la serie mensual de cumplimiento</caption>
            <thead><tr><th scope="col">Mes</th><th scope="col">Cortes</th><th scope="col">Anunciado</th><th scope="col">Duró</th><th scope="col">Índice</th></tr></thead>
            <tbody>{serie.map((punto) => (
              <tr key={punto.periodo}>
                <th scope="row">{mesEnPalabras(punto.periodo)}</th>
                <td>{punto.cantidadCortes === undefined ? 'Sin dato' : formatearNumero(punto.cantidadCortes)}</td>
                <td>{punto.duracionPrometidaSegundos === undefined ? 'Sin dato' : duracionCorta(punto.duracionPrometidaSegundos)}</td>
                <td>{punto.duracionRealSegundos === undefined ? 'Sin dato' : duracionCorta(punto.duracionRealSegundos)}</td>
                <td>{punto.porcentajeCumplimiento === undefined ? 'Sin dato' : formatearPorcentaje(punto.porcentajeCumplimiento)}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
        <a href={csv} download className={estilos.csv}>Descargar en CSV</a>
      </details>
    </section>
  )
}

/** RF020–RF022, RF024 · `ADR-079`: el titular es el veredicto y las cifras son por corte. */
export function Cumplimiento() {
  const buscar = useSearch({ from: '/publico/cumplimiento' })
  const navegar = useNavigate()
  const { sectores, lectura, reintentar: reintentarListado } = useListado()
  const sector = sectores.find((candidato) => candidato.id === buscar.sector)
  const barrioNoEncontrado = !!buscar.sector && !!lectura.listado && !sector
  const indice = useIndiceCumplimiento(buscar.sector)
  const serie = useSerieCumplimiento({ sectorId: buscar.sector }, !barrioNoEncontrado)
  const errorIndice = indice.error as { estado?: number } | null
  const sinCortes = indice.isError && errorIndice?.estado === 400
  const resumen = resumirCumplimiento(indice.data, serie.data)
  const barrio = sector ? nombreLegible(sector.nombre) : undefined
  const periodo = resumen && periodoEnPalabras(resumen.primerMes, resumen.ultimoMes)
  const cargando = !barrioNoEncontrado && (indice.isPending || (indice.isSuccess && serie.isPending))
  const csv = `/api/cumplimiento/serie.csv${buscar.sector ? `?sectorId=${encodeURIComponent(buscar.sector)}` : ''}`

  const titular = resumen
    ? titularVeredicto(resumen.diferenciaPorCorte, barrio)
    : barrio ? `Lo prometido y lo que duró en ${barrio}` : 'Lo prometido y lo que duró'

  return (
    <div className={`${estilosPagina.pagina} ${estilos.pagina}`}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>
          Cumplimiento
          {resumen && <> · {formatearNumero(resumen.cortes)} {resumen.cortes === 1 ? 'corte cerrado' : 'cortes cerrados'}</>}
          {periodo && <> · {periodo}</>}
        </p>
        <h1 className={`${estilosPagina.titular} ${estilos.titular}`}>{titular}</h1>
        <div className={estilos.barra}>
          <SelectorBarrio sectores={sectores} valor={buscar.sector} etiqueta="Barrio"
            alCambiar={(id) => void navegar({ to: '/cumplimiento', search: id ? { sector: id } : {}, replace: true })} />
          {lectura.error && !sectores.length && <p role="alert">No pudimos cargar los barrios. <button type="button" className={estilos.accion} onClick={reintentarListado}>Reintentar</button></p>}
        </div>
      </header>

      {barrioNoEncontrado && <p className={estilos.vacio}>No encontramos este barrio.</p>}
      {cargando && <output className={estilos.esqueleto}>Consultando el cumplimiento…<span /><span /><span /></output>}
      {!barrioNoEncontrado && sinCortes && (
        <p className={estilos.vacio}>Aún no hay cortes cerrados para medir{barrio ? ` en ${barrio}` : ''}. Un corte abierto todavía no se puede medir.</p>
      )}
      {!barrioNoEncontrado && indice.isError && !sinCortes && (
        <p role="alert">No pudimos consultar el cumplimiento. Revisa tu conexión e inténtalo otra vez. <button type="button" className={estilos.accion} onClick={() => void indice.refetch()}>Reintentar</button></p>
      )}
      {!barrioNoEncontrado && indice.data && !cargando && (
        <div className={estilos.columnas}>
          {resumen ? <Respuesta resumen={resumen} /> : <RespuestaSinSerie indice={indice.data} />}
          {serie.isError && <p role="alert">No pudimos consultar el mes a mes. <button type="button" className={estilos.accion} onClick={() => void serie.refetch()}>Reintentar</button></p>}
          {serie.data && serie.data.length > 0 && <MesAMes puntos={serie.data} csv={csv} />}
        </div>
      )}
    </div>
  )
}
