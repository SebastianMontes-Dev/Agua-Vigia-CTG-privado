import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useEstadisticas } from '../../app/datos'
import { formatearNumero } from '../../dominio/formato'
import { diasExtremos, disponibilidadEstadisticas, listaDeDias, ordenarDias, titularEstadisticas } from '../../dominio/estadisticas'
import { nombreLegible } from '../../dominio/sectores'
import estilosPagina from './Pagina.module.css'
import estilos from './Estadisticas.module.css'

const BARRIOS_VISIBLES = 5
const ABREVIADO: Record<string, string> = {
  Lunes: 'Lun', Martes: 'Mar', Miércoles: 'Mié', Jueves: 'Jue', Viernes: 'Vie', Sábado: 'Sáb', Domingo: 'Dom',
}

const cortes = (cantidad: number) => `${formatearNumero(cantidad)} ${cantidad === 1 ? 'corte' : 'cortes'}`

/** RF023, RF025 · `ADR-079`: el titular es la duración promedio; los días, en un gráfico compacto al lado. */
export function Estadisticas() {
  const [todosLosBarrios, setTodosLosBarrios] = useState(false)
  const consulta = useEstadisticas()
  const datos = consulta.data
  const disponibilidad = datos ? disponibilidadEstadisticas(datos) : null
  const dias = ordenarDias(datos?.cortesPorDiaDeSemana)
  const mas = diasExtremos(dias, 'mas')
  const menos = diasExtremos(dias, 'menos')
  const maximo = Math.max(1, ...dias.map((dia) => dia.cortes ?? 0))
  const barrios = (datos?.sectoresMasAfectados ?? []).map((sector) => ({
    clave: sector.sectorId ?? '',
    nombre: sector.nombre && sector.nombre.trim().toLowerCase() !== 'desconocido'
      ? nombreLegible(sector.nombre) : sector.sectorId ?? 'Barrio sin identificar',
    avisos: sector.cantidadCortes ?? null,
  }))
  const maximoAvisos = Math.max(1, ...barrios.map((barrio) => barrio.avisos ?? 0))
  const barriosVisibles = todosLosBarrios ? barrios : barrios.slice(0, BARRIOS_VISIBLES)

  return (
    <div className={`${estilosPagina.pagina} ${estilos.pagina}`}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Estadísticas · todos los cortes registrados</p>
        <h1 className={`${estilosPagina.titular} ${estilos.titular}`}>
          {titularEstadisticas(disponibilidad?.duracionMedible ?? null)}
        </h1>
      </header>

      {consulta.isPending && <output className={estilos.esqueleto}>Consultando las estadísticas…<span /><span /><span /></output>}
      {consulta.isError && (
        <p role="alert">
          No pudimos consultar las estadísticas. Revisa tu conexión e inténtalo otra vez.{' '}
          <button type="button" className={estilos.accion} onClick={() => void consulta.refetch()}>Reintentar</button>
        </p>
      )}
      {disponibilidad?.vacioGeneral && <p className={estilos.vacio}>Todavía no hay datos registrados para resumir.</p>}

      {datos && disponibilidad && !disponibilidad.vacioGeneral && (
        <div className={estilos.columnas}>
          <div className={estilos.respuesta}>
            <dl className={estilos.cifras}>
              <div><dt>Cortes registrados</dt><dd>{formatearNumero(disponibilidad.totalCortes)}</dd></div>
              <div><dt>Más cortes</dt><dd>{mas ? listaDeDias(mas.dias) : 'Sin diferencia'}</dd></div>
              <div><dt>Menos cortes</dt><dd>{menos ? listaDeDias(menos.dias) : 'Sin diferencia'}</dd></div>
            </dl>
            <p className={estilos.nota}>
              {disponibilidad.duracionMedible === null
                ? 'La duración promedio aparece cuando haya cortes cerrados.'
                : 'La duración promedio cuenta solo los cortes cerrados.'}{' '}
              <Link to="/cumplimiento" className={estilos.enlace}>¿Duran lo anunciado?</Link>
            </p>

            <section className={`${estilos.bloque} ${estilos.barriosBloque}`} aria-labelledby="titulo-barrios">
              <h2 id="titulo-barrios">Barrios más nombrados en avisos de corte</h2>
              {barrios.length === 0 ? (
                <p className={estilos.nota}>Ningún boletín aprobado de Acuacar menciona barrios todavía.</p>
              ) : (
                <>
                  <ol className={estilos.barrios}>
                    {barriosVisibles.map((barrio) => (
                      <li key={barrio.clave}>
                        <span>{barrio.nombre}</span>
                        <span className={estilos.ejeBarrio} aria-hidden="true">
                          <span style={{ width: `${(barrio.avisos ?? 0) / maximoAvisos * 100}%` }} />
                        </span>
                        <strong>{barrio.avisos === null ? 'Sin dato' : `${formatearNumero(barrio.avisos)} ${barrio.avisos === 1 ? 'aviso' : 'avisos'}`}</strong>
                      </li>
                    ))}
                  </ol>
                  {barrios.length > BARRIOS_VISIBLES && !todosLosBarrios && (
                    <button type="button" className={estilos.accion} onClick={() => setTodosLosBarrios(true)}>
                      Ver los {formatearNumero(barrios.length)} barrios
                    </button>
                  )}
                </>
              )}
            </section>
          </div>

          <section className={estilos.evidencia} aria-labelledby="titulo-dias">
            <header className={estilos.cabeceraBloque}>
              <h2 id="titulo-dias">Cortes por día de la semana</h2>
              <p>{mas ? `Más los ${listaDeDias(mas.dias).toLowerCase()}: ${cortes(mas.cortes)}` : 'Sin diferencias entre días'}</p>
            </header>
            {disponibilidad.totalCortes === 0 ? (
              <p className={estilos.nota}>Todavía no hay cortes registrados.</p>
            ) : (
              <ol className={estilos.columnasDias} aria-label="Cortes de lunes a domingo">
                {dias.map(({ dia, cortes: cantidad }) => (
                  <li key={dia} className={mas?.dias.includes(dia) ? estilos.destacado : undefined}>
                    <strong>{cantidad === null ? '–' : formatearNumero(cantidad)}</strong>
                    <span className={estilos.columna} aria-hidden="true">
                      <span style={{ height: `${(cantidad ?? 0) / maximo * 100}%` }} />
                    </span>
                    <abbr title={dia}>{ABREVIADO[dia] ?? dia}</abbr>
                  </li>
                ))}
              </ol>
            )}
            <details className={estilos.datos}>
              <summary>Ver datos</summary>
              <div className={estilos.tablaContenedor}>
                <table>
                  <caption>Cortes registrados de lunes a domingo</caption>
                  <thead><tr><th scope="col">Día</th><th scope="col">Cortes</th></tr></thead>
                  <tbody>{dias.map(({ dia, cortes: cantidad }) => (
                    <tr key={dia}><th scope="row">{dia}</th><td>{cantidad === null ? 'Sin dato' : formatearNumero(cantidad)}</td></tr>
                  ))}</tbody>
                </table>
                {barrios.length > 0 && (
                  <table>
                    <caption>Avisos de corte aprobados por barrio</caption>
                    <thead><tr><th scope="col">Barrio</th><th scope="col">Avisos</th></tr></thead>
                    <tbody>{barrios.map((barrio) => (
                      <tr key={barrio.clave}><th scope="row">{barrio.nombre}</th><td>{barrio.avisos === null ? 'Sin dato' : formatearNumero(barrio.avisos)}</td></tr>
                    ))}</tbody>
                  </table>
                )}
              </div>
              <a href="/api/estadisticas/exportar.csv" download className={estilos.csv}>Descargar en CSV</a>
            </details>
          </section>
        </div>
      )}
    </div>
  )
}
