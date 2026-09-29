import { useEffect, useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import type { components } from '../../api/generado/esquema'
import { useBitacora, useListado, useSustento } from '../../app/datos'
import { useAhora } from '../../app/ahora'
import { SelectorBarrio } from '../../componentes/SelectorBarrio'
import { GlifoEstado } from '../../componentes/GlifoEstado'
import { presentarEstado, type EstadoServicio } from '../../dominio/estados'
import { esTipoBitacora, imagenDeBitacora, limiteDiaCartagena, NOMBRE_TIPO, rangoDiasValido, TIPOS_BITACORA } from '../../dominio/historia'
import { formatearNumero } from '../../dominio/formato'
import { momento } from '../../dominio/tiempo'
import estilosPagina from './Pagina.module.css'
import estilos from './Bitacora.module.css'

type Evento = components['schemas']['EventoBitacoraRespuesta']

function estadoValido(valor?: string): EstadoServicio | null {
  return valor === 'CON_SERVICIO' || valor === 'SIN_SERVICIO' || valor === 'PRESION_BAJA' || valor === 'CORTE_PROGRAMADO'
    ? valor : null
}

function Sustento({ id, cantidad }: { id: string; cantidad: number }) {
  const [abierto, setAbierto] = useState(false)
  const consulta = useSustento(id, abierto)
  return (
    <div className={estilos.sustento}>
      <p>Sustentado por {formatearNumero(cantidad)} {cantidad === 1 ? 'reporte' : 'reportes'}</p>
      <button type="button" aria-expanded={abierto} onClick={() => setAbierto(!abierto)}>
        {abierto ? 'Ocultar referencias de sustento' : 'Ver referencias de sustento'}
      </button>
      {abierto && <div className={estilos.referencias}>
        <p>Referencias de trazabilidad; el contenido de los reportes no es público.</p>
        {consulta.isPending && <output>Consultando referencias…</output>}
        {consulta.isError && <p role="alert">No pudimos consultar las referencias. <button type="button" onClick={() => consulta.refetch()}>Reintentar</button></p>}
        {consulta.data && <>
          <ul>{consulta.data.pages.flatMap((pagina) => pagina.ids).map((referencia) => <li key={referencia}><code>{referencia}</code></li>)}</ul>
          {consulta.hasNextPage && <button type="button" disabled={consulta.isFetchingNextPage} onClick={() => consulta.fetchNextPage()}>
            {consulta.isFetchingNextPage ? 'Consultando…' : 'Cargar más referencias'}
          </button>}
        </>}
      </div>}
    </div>
  )
}

function Portada({ url }: { url: string }) {
  const [fallo, setFallo] = useState(false)
  if (fallo) return null
  return <img src={url} alt="Portada del boletín" loading="lazy" onError={() => setFallo(true)} className={estilos.portada} />
}

function EventoBitacora({ evento, ahora }: { evento: Evento; ahora: Date }) {
  const estado = estadoValido(evento.estado)
  const imagen = imagenDeBitacora(evento.imagenUrl)
  const tipo = esTipoBitacora(evento.tipo) ? evento.tipo : null
  return (
    <li className={estilos.evento}>
      <time className={estilos.fecha} dateTime={evento.timestamp}>{evento.timestamp ? momento(evento.timestamp, ahora) : 'Sin fecha de registro'}</time>
      <div className={estilos.contenido}>
        <p className={estilos.procedencia}>{tipo ? NOMBRE_TIPO[tipo] : 'Evento registrado'}</p>
        <p className={estilos.descripcion}>{evento.descripcion ?? 'Sin descripción disponible'}</p>
        <p className={estilos.estado}>{estado ? <><GlifoEstado estado={estado} tamano={16} />{presentarEstado(estado).texto}</> : 'Informativo'}</p>
        {imagen && <Portada url={imagen} />}
        <p className={estilos.fuente}>{evento.urlOriginal
          ? <a href={evento.urlOriginal} target="_blank" rel="noopener noreferrer">{tipo === 'CORTE_DETECTADO_POR_INGESTA' ? 'Boletín de Acuacar' : 'Ver fuente original'}</a>
          : 'Sin enlace a la fuente'}</p>
        {(evento.cantidadReportesSustento ?? 0) > 0 && evento.id && <Sustento id={evento.id} cantidad={evento.cantidadReportesSustento!} />}
      </div>
    </li>
  )
}

export function Bitacora() {
  const buscar = useSearch({ from: '/publico/bitacora' })
  const navegar = useNavigate()
  const { sectores, lectura, reintentar: reintentarListado } = useListado()
  const ahora = useAhora()
  const valido = rangoDiasValido(buscar.desde, buscar.hasta)
  const consulta = useBitacora({ sector: buscar.sector, tipo: buscar.tipo,
    desde: buscar.desde ? limiteDiaCartagena(buscar.desde) ?? undefined : undefined,
    hasta: buscar.hasta ? limiteDiaCartagena(buscar.hasta, true) ?? undefined : undefined })
  const eventos = consulta.data?.pages.flatMap((pagina) => pagina.eventos) ?? []
  const error = consulta.error as { estado?: number } | null
  const actualizar = (cambio: Partial<typeof buscar>) => navegar({ to: '/bitacora', search: { ...buscar, ...cambio }, replace: true })
  const hayFiltros = Boolean(buscar.sector || buscar.tipo || buscar.desde || buscar.hasta)
  const [filtrosAbiertos, setFiltrosAbiertos] = useState(() => hayFiltros || (typeof window !== 'undefined' && window.matchMedia?.('(min-width: 768px)').matches))
  useEffect(() => {
    const escritorio = window.matchMedia?.('(min-width: 768px)')
    if (!escritorio) return
    const ajustar = () => setFiltrosAbiertos(escritorio.matches || hayFiltros)
    escritorio.addEventListener('change', ajustar)
    return () => escritorio.removeEventListener('change', ajustar)
  }, [hayFiltros])

  return (
    <div className={`${estilosPagina.pagina} ${estilos.pagina}`}>
      <header className={estilos.cabecera}>
        <h1 className={estilosPagina.titular}>Bitácora</h1>
        <p className={estilosPagina.entrada}>Cortes y cambios del agua en Cartagena, con su fuente.</p>
      </header>
      <details className={estilos.filtros} open={filtrosAbiertos} onToggle={(evento) => setFiltrosAbiertos(evento.currentTarget.open)}>
        <summary>Filtrar eventos</summary>
        <div className={estilos.campos} aria-label="Filtros de la bitácora">
          <SelectorBarrio sectores={sectores} valor={buscar.sector} alCambiar={(sector) => actualizar({ sector })} />
          <label className={estilos.campo}>Tipo de evento
            <select value={buscar.tipo ?? ''} onChange={(e) => actualizar({ tipo: esTipoBitacora(e.target.value) ? e.target.value : undefined })}>
              <option value="">Todos los tipos</option>
              {TIPOS_BITACORA.map((tipo) => <option key={tipo} value={tipo}>{NOMBRE_TIPO[tipo]}</option>)}
            </select>
          </label>
          <label className={estilos.campo}>Desde (día de Cartagena)
            <input type="date" value={buscar.desde ?? ''} onChange={(e) => actualizar({ desde: e.target.value || undefined })} />
          </label>
          <label className={estilos.campo}>Hasta (inclusive, día de Cartagena)
            <input type="date" value={buscar.hasta ?? ''} onChange={(e) => actualizar({ hasta: e.target.value || undefined })} />
          </label>
          {(!valido || error?.estado === 400) && <p role="alert" className={estilos.errorFiltro}>
            Revisa los filtros: la fecha final debe ser igual o posterior a la inicial y el tipo debe ser válido.
          </p>}
          {lectura.error && !sectores.length && <p role="alert" className={estilos.errorFiltro}>No pudimos cargar los barrios. <button type="button" onClick={reintentarListado}>Reintentar</button></p>}
        </div>
      </details>
      <section aria-label="Eventos de la bitácora" className={estilos.resultados}>
        {consulta.isPending && <output className={estilos.esqueleto}>Consultando la bitácora…<span /><span /><span /></output>}
        {consulta.isError && error?.estado !== 400 && <p role="alert">No pudimos consultar la bitácora. Revisa tu conexión e inténtalo otra vez. <button type="button" onClick={() => consulta.refetch()}>Reintentar</button></p>}
        {consulta.data && <>
          <p className={estilos.total}>{formatearNumero(consulta.data.pages[0]?.paginacion.total ?? eventos.length)} eventos con estos filtros</p>
          {eventos.length === 0 ? <p className={estilos.vacio}>No hay eventos con esos filtros</p> : <ol className={estilos.lista}>{eventos.map((evento, indice) => <EventoBitacora key={evento.id ?? indice} evento={evento} ahora={ahora} />)}</ol>}
          {consulta.hasNextPage && <button type="button" className={estilos.mas} disabled={consulta.isFetchingNextPage} onClick={() => consulta.fetchNextPage()}>
            {consulta.isFetchingNextPage ? 'Consultando…' : 'Cargar más'}
          </button>}
        </>}
      </section>
    </div>
  )
}
