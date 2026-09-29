import { useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import type { components } from '../../api/generado/esquema'
import { useBitacora, useConteosBitacora, useListado, useSustento } from '../../app/datos'
import { useAhora } from '../../app/ahora'
import { SelectorBarrio } from '../../componentes/SelectorBarrio'
import { GlifoEstado } from '../../componentes/GlifoEstado'
import { presentarEstado, type EstadoServicio } from '../../dominio/estados'
import {
  descripcionLegible,
  esTipoBitacora,
  FILTRO_TIPO,
  imagenDeBitacora,
  limiteDiaCartagena,
  NOMBRE_TIPO,
  rangoDiasValido,
  TIPOS_BITACORA,
  type TipoBitacora,
} from '../../dominio/historia'
import { formatearNumero } from '../../dominio/formato'
import { nombreLegible } from '../../dominio/sectores'
import { claveDiaCartagena, etiquetaDia, formatearHora, haceCuanto } from '../../dominio/tiempo'
import estilosPagina from './Pagina.module.css'
import estilos from './Bitacora.module.css'

type Evento = components['schemas']['EventoBitacoraRespuesta']

function estadoValido(valor?: string | null): EstadoServicio | null {
  return valor === 'CON_SERVICIO' || valor === 'SIN_SERVICIO' || valor === 'PRESION_BAJA' || valor === 'CORTE_PROGRAMADO'
    ? valor : null
}

function Sustento({ id, cantidad }: { id: string; cantidad: number }) {
  const [abierto, setAbierto] = useState(false)
  const consulta = useSustento(id, abierto)
  return (
    <>
      <button type="button" className={estilos.enlace} aria-expanded={abierto} onClick={() => setAbierto(!abierto)}>
        Sustentado por {formatearNumero(cantidad)} {cantidad === 1 ? 'reporte' : 'reportes'}
      </button>
      {abierto && (
        <div className={estilos.referencias}>
          <p>Referencias de trazabilidad; el contenido de los reportes no es público.</p>
          {consulta.isPending && <output>Consultando referencias…</output>}
          {consulta.isError && <p role="alert">No pudimos consultar las referencias. <button type="button" className={estilos.enlace} onClick={() => void consulta.refetch()}>Reintentar</button></p>}
          {consulta.data && (
            <>
              <ul>{consulta.data.pages.flatMap((pagina) => pagina.ids).map((referencia) => <li key={referencia}><code>{referencia}</code></li>)}</ul>
              {consulta.hasNextPage && (
                <button type="button" className={estilos.enlace} disabled={consulta.isFetchingNextPage} onClick={() => void consulta.fetchNextPage()}>
                  {consulta.isFetchingNextPage ? 'Consultando…' : 'Cargar más referencias'}
                </button>
              )}
            </>
          )}
        </div>
      )}
    </>
  )
}

function Portada({ url }: { url: string }) {
  const [fallo, setFallo] = useState(false)
  if (fallo) return null
  return <img src={url} alt="Portada del boletín" loading="lazy" onError={() => setFallo(true)} className={estilos.portada} />
}

/** `identidad.md` §4.1: una o dos líneas por evento; lo que no existe (fuente, estado) no se nombra. */
function EventoBitacora({ evento, nombreDe, originalDe }: {
  evento: Evento
  nombreDe: (id: string) => string | undefined
  originalDe: (id: string) => string | undefined
}) {
  const estado = estadoValido(evento.estado)
  const imagen = imagenDeBitacora(evento.imagenUrl)
  const tipo = esTipoBitacora(evento.tipo) ? evento.tipo : null
  const barrio = evento.sectorId ? nombreDe(evento.sectorId) ?? evento.sectorId : null
  const original = evento.sectorId ? originalDe(evento.sectorId) : undefined
  const sustento = evento.cantidadReportesSustento ?? 0
  return (
    <li className={estilos.evento}>
      <time className={estilos.hora} dateTime={evento.timestamp}>{evento.timestamp ? formatearHora(evento.timestamp) : 'Sin hora'}</time>
      <div className={estilos.contenido}>
        <p className={estilos.titulo}>
          {estado && <GlifoEstado estado={estado} tamano={16} />}
          {barrio && <strong>{barrio}</strong>}
          <span className={estilos.tipo}>{tipo ? NOMBRE_TIPO[tipo] : 'Evento registrado'}{estado && ` · ${presentarEstado(estado).texto}`}</span>
        </p>
        {evento.descripcion && <p className={estilos.descripcion}>{descripcionLegible(evento.descripcion, nombreDe, original && barrio ? { original, legible: barrio } : undefined)}</p>}
        {(evento.urlOriginal || (sustento > 0 && evento.id)) && (
          <p className={estilos.acciones}>
            {evento.urlOriginal && (
              <a href={evento.urlOriginal} target="_blank" rel="noopener noreferrer" className={estilos.enlace}>
                {tipo === 'CORTE_DETECTADO_POR_INGESTA' ? 'Boletín de Acuacar' : 'Ver fuente original'}
              </a>
            )}
            {sustento > 0 && evento.id && <Sustento id={evento.id} cantidad={sustento} />}
          </p>
        )}
      </div>
      {imagen && <Portada url={imagen} />}
    </li>
  )
}

function agruparPorDia(eventos: readonly Evento[], ahora: Date) {
  const grupos: { clave: string; etiqueta: string; eventos: Evento[] }[] = []
  for (const evento of eventos) {
    const clave = evento.timestamp ? claveDiaCartagena(evento.timestamp) : 'sin-fecha'
    const ultimo = grupos.at(-1)
    if (ultimo?.clave === clave) ultimo.eventos.push(evento)
    else grupos.push({ clave, etiqueta: evento.timestamp ? etiquetaDia(evento.timestamp, ahora) : 'Sin fecha de registro', eventos: [evento] })
  }
  return grupos
}

/** RF011, RF026–RF028 · `ADR-079`: el titular dice cuándo fue el último cambio; los tipos son pestañas con su cantidad. */
export function Bitacora() {
  const buscar = useSearch({ from: '/publico/bitacora' })
  const navegar = useNavigate()
  const { sectores, lectura, reintentar: reintentarListado } = useListado()
  const ahora = useAhora()
  const valido = rangoDiasValido(buscar.desde, buscar.hasta)
  const rango = {
    sector: buscar.sector,
    desde: buscar.desde ? limiteDiaCartagena(buscar.desde) ?? undefined : undefined,
    hasta: buscar.hasta ? limiteDiaCartagena(buscar.hasta, true) ?? undefined : undefined,
  }
  const consulta = useBitacora({ ...rango, tipo: buscar.tipo })
  const conteos = useConteosBitacora(rango, TIPOS_BITACORA)
  const eventos = consulta.data?.pages.flatMap((pagina) => pagina.eventos) ?? []
  const total = consulta.data?.pages[0]?.paginacion.total ?? null
  const error = consulta.error as { estado?: number } | null
  const actualizar = (cambio: Partial<typeof buscar>) => void navegar({ to: '/bitacora', search: { ...buscar, ...cambio }, replace: true })
  const nombres = new Map(sectores.map((sector) => [sector.id, nombreLegible(sector.nombre)]))
  const originales = new Map(sectores.map((sector) => [sector.id, sector.nombre]))
  const nombreDe = (id: string) => nombres.get(id)
  const originalDe = (id: string) => originales.get(id)
  const barrioFiltrado = buscar.sector ? nombreDe(buscar.sector) ?? buscar.sector : null
  const conteoTotal = TIPOS_BITACORA.every((tipo) => conteos[tipo] !== null)
    ? TIPOS_BITACORA.reduce((suma, tipo) => suma + (conteos[tipo] ?? 0), 0) : null

  const ultimo = eventos[0]
  const barrioUltimo = ultimo?.sectorId ? nombreDe(ultimo.sectorId) ?? null : null
  const titular = ultimo?.timestamp
    ? `El último cambio fue ${haceCuanto(ultimo.timestamp, ahora)}${barrioUltimo && !barrioFiltrado ? `, en ${barrioUltimo}` : ''}`
    : 'Cada cambio del agua, con su fuente'

  const pestana = (tipo: TipoBitacora | undefined, texto: string, cantidad: number | null) => (
    <button
      key={tipo ?? 'todos'}
      type="button"
      className={estilos.pestana}
      aria-pressed={buscar.tipo === tipo}
      onClick={() => actualizar({ tipo })}
    >
      {texto}{cantidad !== null && <>{' '}<span className={estilos.cantidad}>{formatearNumero(cantidad)}</span></>}
    </button>
  )

  return (
    <div className={`${estilosPagina.pagina} ${estilos.pagina}`}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>
          Bitácora{barrioFiltrado && <> · {barrioFiltrado}</>}{total !== null && <> · {formatearNumero(total)} {total === 1 ? 'evento' : 'eventos'}</>}
        </p>
        <h1 className={`${estilosPagina.titular} ${estilos.titular}`}>{titular}</h1>
      </header>

      <div className={estilos.columnas}>
        <aside className={estilos.filtros} aria-label="Filtros de la bitácora">
          <fieldset className={estilos.pestanas}>
            <legend className={estilos.oculto}>Tipo de evento</legend>
            {pestana(undefined, 'Todos', conteoTotal)}
            {TIPOS_BITACORA.map((tipo) => pestana(tipo, FILTRO_TIPO[tipo], conteos[tipo]))}
          </fieldset>
          <details className={estilos.masFiltros} open={Boolean(buscar.sector || buscar.desde || buscar.hasta) || undefined}>
            <summary>Barrio y fechas{(buscar.sector || buscar.desde || buscar.hasta) && ' · activos'}</summary>
            <div className={estilos.campos}>
              <SelectorBarrio sectores={sectores} valor={buscar.sector} alCambiar={(sector) => actualizar({ sector })} />
              <label className={estilos.campo}>Desde (día de Cartagena)
                <input type="date" value={buscar.desde ?? ''} onChange={(e) => actualizar({ desde: e.target.value || undefined })} />
              </label>
              <label className={estilos.campo}>Hasta (inclusive, día de Cartagena)
                <input type="date" value={buscar.hasta ?? ''} onChange={(e) => actualizar({ hasta: e.target.value || undefined })} />
              </label>
            </div>
          </details>
          {(!valido || error?.estado === 400) && (
            <p role="alert" className={estilos.errorFiltro}>Revisa las fechas: la final debe ser igual o posterior a la inicial.</p>
          )}
          {lectura.error && !sectores.length && (
            <p role="alert" className={estilos.errorFiltro}>No pudimos cargar los barrios. <button type="button" className={estilos.enlace} onClick={reintentarListado}>Reintentar</button></p>
          )}
        </aside>

        <section aria-label="Eventos de la bitácora" className={estilos.resultados}>
          {consulta.isPending && <output className={estilos.esqueleto}>Consultando la bitácora…<span /><span /><span /></output>}
          {consulta.isError && error?.estado !== 400 && (
            <p role="alert">No pudimos consultar la bitácora. Revisa tu conexión e inténtalo otra vez. <button type="button" className={estilos.enlace} onClick={() => void consulta.refetch()}>Reintentar</button></p>
          )}
          {consulta.data && eventos.length === 0 && <p className={estilos.vacio}>No hay eventos con esos filtros</p>}
          {agruparPorDia(eventos, ahora).map((grupo) => (
            <section key={grupo.clave} className={estilos.dia} aria-label={grupo.etiqueta}>
              <h2 className={estilos.etiquetaDia}>{grupo.etiqueta}</h2>
              <ol className={estilos.lista}>
                {grupo.eventos.map((evento, indice) => <EventoBitacora key={evento.id ?? `${grupo.clave}-${indice}`} evento={evento} nombreDe={nombreDe} originalDe={originalDe} />)}
              </ol>
            </section>
          ))}
          {consulta.hasNextPage && (
            <button type="button" className={estilos.mas} disabled={consulta.isFetchingNextPage} onClick={() => void consulta.fetchNextPage()}>
              {consulta.isFetchingNextPage ? 'Consultando…' : 'Cargar más eventos'}
            </button>
          )}
        </section>
      </div>
    </div>
  )
}
