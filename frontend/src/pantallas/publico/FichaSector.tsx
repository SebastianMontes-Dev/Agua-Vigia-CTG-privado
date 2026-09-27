import { Link, useParams } from '@tanstack/react-router'
import { lazy, Suspense, useMemo } from 'react'
import { useAhora } from '../../app/ahora'
import { useCortes, useListado } from '../../app/datos'
import { CorteAbierto } from '../../componentes/CorteAbierto'
import { CortesCerrados } from '../../componentes/CortesCerrados'
import { EstadoListado } from '../../componentes/EstadoListado'
import { GlifoEstado } from '../../componentes/GlifoEstado'
import { estaAbierto, estaCerrado } from '../../dominio/cortes'
import { presentarEstado } from '../../dominio/estados'
import { sinVerificacionReciente } from '../../dominio/frescura'
import { nombreLegible, type Sector } from '../../dominio/sectores'
import { haceCuanto } from '../../dominio/tiempo'
import estilos from './Panel.module.css'

const numero = new Intl.NumberFormat('es-CO')
// El cajón del reporte solo se necesita al tocar el botón: no pesa en la primera respuesta.
const Reporte = lazy(() => import('./Reporte').then((modulo) => ({ default: modulo.Reporte })))

function Volver() {
  return <Link to="/" className={estilos.volver}><span aria-hidden="true">←</span> Cartagena ahora</Link>
}

function Registro({ sector, ahora }: { sector: Sector; ahora: Date }) {
  if (!sector.actualizadoEn) return <p className={estilos.nota}>Sin fecha de registro.</p>
  return (
    <p className={estilos.nota}>
      Estado registrado <time dateTime={sector.actualizadoEn}>{haceCuanto(sector.actualizadoEn, ahora)}</time>
      {sector.verificadoEn && <> · verificado <time dateTime={sector.verificadoEn}>{haceCuanto(sector.verificadoEn, ahora)}</time></>}
    </p>
  )
}

function useCortesDelSector(sector: Sector) {
  const consulta = useCortes(sector.id ?? '')
  const cortes = useMemo(() => consulta.data?.pages.flatMap((pagina) => pagina.cortes) ?? [], [consulta.data])
  return { consulta, cortes }
}

function Horario({ sector, ahora }: { sector: Sector; ahora: Date }) {
  const { consulta, cortes } = useCortesDelSector(sector)
  const abiertos = cortes.filter(estaAbierto)
  const afectado = sector.estado && sector.estado !== 'CON_SERVICIO'
  if (!consulta.isPending && !consulta.isError && abiertos.length === 0 && !afectado) return null

  return (
    <section aria-label="Horario del corte" className={estilos.bloque} aria-busy={consulta.isPending}>
      {consulta.isPending ? (
        <div className={estilos.esqueleto} aria-hidden="true"><span /><span /></div>
      ) : consulta.isError && cortes.length === 0 ? (
        <div className={estilos.alerta} role="alert">
          <p>No pudimos consultar los cortes de este barrio.</p>
          <button type="button" className={estilos.botonSecundario} onClick={() => consulta.refetch()}>Reintentar</button>
        </div>
      ) : abiertos.length > 0 ? (
        abiertos.map((corte) => <CorteAbierto key={corte.id} corte={corte} ahora={ahora} />)
      ) : (
        <p className={estilos.nota}>Hora de restablecimiento no informada.</p>
      )}
    </section>
  )
}

function Historial({ sector, ahora }: { sector: Sector; ahora: Date }) {
  const { consulta, cortes } = useCortesDelSector(sector)
  const cerrados = cortes.filter(estaCerrado)
  return (
    <section aria-labelledby="titulo-cerrados" className={estilos.bloque}>
      <h2 id="titulo-cerrados" className={estilos.seccion}>Cortes cerrados en {nombreLegible(sector.nombre)}</h2>
      {consulta.isPending || (consulta.isError && cortes.length === 0) ? null : cerrados.length === 0 ? (
        <p className={estilos.nota}>No hay cortes cerrados registrados para este barrio.</p>
      ) : (
        <CortesCerrados cortes={cerrados} ahora={ahora} />
      )}
      {consulta.hasNextPage && (
        <button
          type="button"
          className={estilos.enlaceBoton}
          disabled={consulta.isFetchingNextPage}
          onClick={() => consulta.fetchNextPage()}
        >
          {consulta.isFetchingNextPage ? 'Consultando…' : 'Ver cortes anteriores'}
        </button>
      )}
    </section>
  )
}

export function FichaSector() {
  const { id } = useParams({ from: '/publico/mapa/sectores/$id' })
  const { lectura, porId, reintentar } = useListado()
  const ahora = useAhora()
  const sector = porId.get(id)

  if (!lectura.listado) {
    return (
      <div className={estilos.contenido}>
        <Volver />
        <EstadoListado lectura={lectura} ahora={ahora} reintentar={reintentar} />
        <div className={estilos.esqueleto} aria-hidden="true"><span /><span /><span /></div>
      </div>
    )
  }

  if (!sector) {
    return (
      <div className={estilos.contenido}>
        <Volver />
        <header className={estilos.entrada}>
          <h1 className={estilos.titular}>No encontramos este barrio</h1>
          <p className={estilos.bajada}>El enlace no corresponde a ninguno de los barrios del listado. Búscalo por su nombre.</p>
        </header>
      </div>
    )
  }

  const nombre = nombreLegible(sector.nombre)
  const estado = presentarEstado(sector.estado)
  const advertir = sinVerificacionReciente(sector.verificadoEn, ahora)

  return (
    <article className={estilos.contenido} aria-labelledby="nombre-barrio">
      <Volver />
      <header className={estilos.ficha}>
        <p className={estilos.rotulo}>
          Barrio · {sector.poblacion == null ? 'sin dato censal' : `${numero.format(sector.poblacion)} habitantes`}
        </p>
        <h1 id="nombre-barrio" className={estilos.barrio}>{nombre}</h1>
        <p className={estilos.estado}>
          <GlifoEstado estado={sector.estado} tamano={24} />
          {estado.texto}
        </p>
        {!sector.estado && <p className={estilos.nota}>Puedes reportar lo que pasa en tu casa.</p>}
        {advertir && (
          <p className={estilos.advertencia}>
            Sin verificación reciente: nadie lo confirma desde hace más de 24 horas. El estado es el último registrado.
          </p>
        )}
      </header>

      <Horario sector={sector} ahora={ahora} />

      <section aria-label="Registro del dato" className={estilos.bloque}>
        <Registro sector={sector} ahora={ahora} />
        <EstadoListado lectura={lectura} ahora={ahora} reintentar={reintentar} />
      </section>

      <section aria-label="Qué puedes hacer" className={`${estilos.bloque} ${estilos.acciones}`}>
        <Suspense fallback={<span className={estilos.reservaBoton} aria-hidden="true" />}>
          <Reporte sector={sector} />
        </Suspense>
        <Link to="/avisos" search={{ sector: sector.id }} className={estilos.enlace}>Recibir avisos de {nombre}</Link>
      </section>

      <Historial sector={sector} ahora={ahora} />
    </article>
  )
}
