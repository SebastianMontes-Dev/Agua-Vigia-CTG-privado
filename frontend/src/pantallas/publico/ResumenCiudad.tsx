import { Link } from '@tanstack/react-router'
import { useId, useMemo, useState } from 'react'
import { useAhora } from '../../app/ahora'
import { useListado } from '../../app/datos'
import { BarraCiudad } from '../../componentes/BarraCiudad'
import { EstadoListado } from '../../componentes/EstadoListado'
import { GlifoEstado } from '../../componentes/GlifoEstado'
import { UsarUbicacion } from '../../componentes/UsarUbicacion'
import { presentarEstado } from '../../dominio/estados'
import { ORDEN_CONTEO, barriosConNovedades, contarPorEstado, nombreLegible, type Sector } from '../../dominio/sectores'
import { haceCuanto } from '../../dominio/tiempo'
import estilos from './Panel.module.css'

const NOVEDADES_VISIBLES = 8
const numero = new Intl.NumberFormat('es-CO')

function FilaSector({ sector, ahora }: { sector: Sector; ahora: Date }) {
  return (
    <li>
      <Link to="/sectores/$id" params={{ id: sector.id ?? '' }} className={estilos.filaEnlace}>
        <GlifoEstado estado={sector.estado} />
        <span className={estilos.filaNombre}>{nombreLegible(sector.nombre)}</span>
        <span className={estilos.filaDato}>
          <span className={estilos.oculto}>{presentarEstado(sector.estado).texto}, </span>
          {sector.actualizadoEn ? <time dateTime={sector.actualizadoEn}>{haceCuanto(sector.actualizadoEn, ahora)}</time> : 'Sin fecha de registro'}
        </span>
      </Link>
    </li>
  )
}

/** Sin barrio elegido: la ciudad ahora (identidad.md §4) y la alternativa textual del mapa (DESIGN.md §7). */
export function ResumenCiudad() {
  const { lectura, sectores, reintentar } = useListado()
  const ahora = useAhora()
  const conteo = useMemo(() => contarPorEstado(sectores), [sectores])
  const novedades = useMemo(() => barriosConNovedades(sectores), [sectores])
  const ordenados = useMemo(() => [...sectores].sort((a, b) => nombreLegible(a.nombre).localeCompare(nombreLegible(b.nombre), 'es')), [sectores])
  const [todasLasNovedades, setTodasLasNovedades] = useState(false)
  const [listaAbierta, setListaAbierta] = useState(false)
  const idLista = useId()
  const hayListado = lectura.listado !== null

  return (
    <div className={estilos.contenido}>
      <EstadoListado lectura={lectura} ahora={ahora} reintentar={reintentar} />

      <header className={estilos.entrada}>
        <h1 className={estilos.titular}>Cartagena ahora</h1>
        <p className={estilos.bajada}>Busca tu barrio o tócalo en el mapa para saber si hay agua y hasta cuándo.</p>
        <UsarUbicacion />
      </header>

      {!hayListado ? (
        <div className={estilos.esqueleto} aria-hidden="true">
          <span /><span /><span /><span /><span />
        </div>
      ) : (
        <>
          <section aria-label="Barrios por estado del agua" className={estilos.bloque}>
            <BarraCiudad conteo={conteo} />
            <ul className={estilos.conteos}>
              {ORDEN_CONTEO.map((clave) => (
                <li key={clave}>
                  <GlifoEstado estado={clave === 'SIN_DATOS' ? null : clave} />
                  <span>{presentarEstado(clave === 'SIN_DATOS' ? null : clave).texto}</span>
                  <span className={`${estilos.cifraFila} cifra`}>{numero.format(conteo[clave])}</span>
                </li>
              ))}
            </ul>
            <p className={estilos.nota}>{numero.format(sectores.length)} barrios en el listado.</p>
          </section>

          <section aria-labelledby="titulo-novedades" className={estilos.bloque}>
            <h2 id="titulo-novedades" className={estilos.seccion}>Barrios con novedades</h2>
            {novedades.length === 0 ? (
              <p className={estilos.nota}>Ningún barrio reporta cortes ni presión baja en este listado.</p>
            ) : (
              <>
                <ul className={estilos.filas}>
                  {(todasLasNovedades ? novedades : novedades.slice(0, NOVEDADES_VISIBLES)).map((sector) => (
                    <FilaSector key={sector.id} sector={sector} ahora={ahora} />
                  ))}
                </ul>
                {novedades.length > NOVEDADES_VISIBLES && (
                  <button type="button" className={estilos.enlaceBoton} onClick={() => setTodasLasNovedades((v) => !v)}>
                    {todasLasNovedades ? 'Ver menos' : `Ver los ${numero.format(novedades.length)} barrios con novedades`}
                  </button>
                )}
              </>
            )}
          </section>

          <section aria-labelledby="titulo-lista" className={estilos.bloque}>
            <h2 id="titulo-lista" className={estilos.seccion}>Todos los barrios</h2>
            <button
              type="button"
              className={estilos.enlaceBoton}
              aria-expanded={listaAbierta}
              aria-controls={idLista}
              onClick={() => setListaAbierta((v) => !v)}
            >
              {listaAbierta ? 'Ocultar la lista' : `Ver la lista de los ${numero.format(sectores.length)} barrios con su estado`}
            </button>
            <ul id={idLista} className={estilos.filas} hidden={!listaAbierta}>
              {listaAbierta && ordenados.map((sector) => <FilaSector key={sector.id} sector={sector} ahora={ahora} />)}
            </ul>
          </section>
        </>
      )}
    </div>
  )
}
