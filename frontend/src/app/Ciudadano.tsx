import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useRouterState } from '@tanstack/react-router'
import { canal, useCanal, useVisible } from './canal'
import { cargarGeometria, mensajeFallo, pedir, type Corte } from '../api/ciudadano'
import { fechaCartagena, habitantes, verificacionAntigua, type Sector } from '../dominio/sectores'
import { Estado } from './Estado'
const HojaReporte = lazy(() => import('./Reporte').then((modulo) => ({ default: modulo.HojaReporte })))
import { aplicarTema, leerPreferenciaTema, type PreferenciaTema } from './tema'
import './ciudadano.css'
import { RespuestaMapa } from '../mapa/RespuestaMapa'
import { MapaSeguro } from '../mapa/MapaSeguro'
const Mapa = lazy(() => import('../mapa/Mapa'))
export function Cabecera() {
  const [tema, setTema] = useState<PreferenciaTema>(leerPreferenciaTema)
  return <><header className="cabecera"><a className="salto" href="#contenido">Saltar al contenido</a><Link className="marca" to="/">AguaVigía<span>CTG</span></Link><nav aria-label="Principal"><Link to="/" activeProps={{ 'aria-current': 'page' }}>Mapa</Link><a className="destino-escritorio" href="/cumplimiento">Cumplimiento</a><a className="destino-escritorio" href="/bitacora">Bitácora</a><a className="destino-escritorio" href="/estadisticas">Estadísticas</a><details className="historial-movil"><summary>Historial</summary><div><a href="/bitacora">Bitácora</a><a href="/cumplimiento">Cumplimiento</a><a href="/estadisticas">Estadísticas</a></div></details><a href="/avisos">Avisos</a></nav><label className="tema">Tema<select aria-label="Tema" value={tema} onChange={(evento) => { const valor = evento.target.value as PreferenciaTema; aplicarTema(valor); setTema(valor) }}><option value="sistema">Sistema</option><option value="claro">Claro</option><option value="oscuro">Oscuro</option></select></label></header></>
}
function Ficha({ id, cerrar, reportar, actual }: { id: string; cerrar: () => void; reportar: (sector: Sector) => void; actual: Sector | undefined }) {
  const visible = useVisible(), [pagina, setPagina] = useState(0)
  const foco = useRef<HTMLHeadingElement>(null)
  const sector = useQuery({ queryKey: ['sector', id], queryFn: async ({ signal }) => (await pedir<Sector>(`/api/sectores/${encodeURIComponent(id)}`, { signal })).dato, enabled: visible })
  const cortes = useQuery({ queryKey: ['cortes', id, pagina], queryFn: async ({ signal }) => {
    const resultado = await pedir<Corte[]>(`/api/sectores/${encodeURIComponent(id)}/cortes?pagina=${pagina}&tamano=5`, { signal })
    return { cortes: resultado.dato, paginas: Number(resultado.respuesta.headers.get('X-Total-Pages') ?? 1), total: Number(resultado.respuesta.headers.get('X-Total-Count') ?? resultado.dato.length) }
  }, enabled: visible })
  const eventos = useQuery({ queryKey: ['eventos-ficha', id], queryFn: async ({ signal }) => (await pedir<unknown[]>(`/api/bitacora?sectorId=${encodeURIComponent(id)}&tamano=1`, { signal })).dato, enabled: visible })
  useEffect(() => { foco.current?.focus() }, [])
  const dato = sector.data ? { ...sector.data, ...actual } : undefined
  return <section className="ficha" aria-labelledby="nombre-barrio"><div className="fila"><span className="rotulo">Tu barrio</span><button onClick={cerrar} className="secundario">Cerrar ficha</button></div><h1 id="nombre-barrio" tabIndex={-1} ref={foco}>{dato?.nombre ?? 'Consultando barrio…'}</h1>
    {sector.isPending && <output>Consultando el estado y su registro…</output>}{sector.isError && <p className="aviso" role="alert">{mensajeFallo(sector.error)} <button onClick={() => { void sector.refetch() }}>Volver a consultar</button></p>}
    {dato && <><Estado estado={dato.estado} /><p className="horario">{cortes.data?.cortes.find((corte) => !corte.finReal)?.finPrometido ? <>Fin prometido: {fechaCartagena(cortes.data.cortes.find((corte) => !corte.finReal)?.finPrometido)}</> : 'Sin horario de restablecimiento publicado'}</p><dl className="registro"><div><dt>Estado registrado</dt><dd>{fechaCartagena(dato.actualizadoEn)}</dd></div><div><dt>Última verificación</dt><dd>{fechaCartagena(dato.verificadoEn)}</dd></div></dl>{verificacionAntigua(dato) && <p className="aviso">Sin verificación reciente. El estado publicado se conserva.</p>}<p className="pequeno">{habitantes(dato.poblacion)}</p><button className="primario" onClick={() => reportar(dato)}>Reportar en este barrio</button></>}
    <div className="historial"><h2>Cortes registrados</h2>{cortes.isPending && <p>Consultando el registro…</p>}{cortes.isError && <p role="alert">No pudimos consultar los cortes. <button onClick={() => { void cortes.refetch() }}>Reintentar consulta</button></p>}{cortes.data && <>{cortes.data.cortes.length === 0 ? <p>No hay cortes registrados para este barrio.</p> : <ul>{cortes.data.cortes.map((corte) => <li key={corte.id}><p>{corte.causa || 'Corte registrado'}</p><p className="pequeno">{fechaCartagena(corte.inicio)} · {corte.finReal ? `Terminó ${fechaCartagena(corte.finReal)}` : 'Sigue abierto'}</p></li>)}</ul>}<div className="fila"><button className="secundario" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button><span className="pequeno">{cortes.data.total} registros</span><button className="secundario" disabled={pagina + 1 >= cortes.data.paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button></div></>}{eventos.data && eventos.data.length > 0 && <a href={`/bitacora?sectorId=${encodeURIComponent(id)}`}>Ver eventos de este barrio →</a>}{eventos.isError && <p className="pequeno">No pudimos comprobar los eventos del barrio.</p>}</div>
  </section>
}
export function Ciudadano() {
  const lectura = useCanal(), visible = useVisible(), navegar = useNavigate()
  const consultas = useQueryClient()
  useEffect(() => { if (!visible) { void consultas.cancelQueries() } }, [visible, consultas])
  const ruta = useRouterState({ select: (estado) => estado.location.pathname })
  const id = ruta.startsWith('/sectores/') ? decodeURIComponent(ruta.slice('/sectores/'.length)) : undefined
  const [buscar, setBuscar] = useState(''), [reporte, setReporte] = useState<{ sector?: Sector } | null>(null)
  const [errorReporte, setErrorReporte] = useState('')
  const abrirReporte = (sector?: Sector) => {
    if (!navigator.onLine) { setErrorReporte('Estás sin conexión. Tu reporte no se guardó. Vuelve a intentarlo a mano cuando tengas red.'); return }
    setErrorReporte(''); setReporte({ sector })
  }
  const geometria = useQuery({ queryKey: ['geometria'], queryFn: cargarGeometria, staleTime: Infinity, enabled: visible })
  const sectores = lectura.listado?.sectores ?? []
  const normalizar = (texto: string) => texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es')
  const filtrados = sectores.filter((sector) => normalizar(sector.nombre ?? '').includes(normalizar(buscar)))
  const abrir = (sectorId: string) => { setBuscar(''); void navegar({ to: '/sectores/$id', params: { id: sectorId } }) }
  const ultimoFoco = useRef<HTMLElement | null>(null)
  const cerrar = () => { void navegar({ to: '/' }); requestAnimationFrame(() => ultimoFoco.current?.focus()) }
  return <><main id="contenido" tabIndex={-1} className={`nucleo ${id ? 'con-ficha' : ''} ${id && buscar ? 'buscando' : ''}`} ><aside className="barrios"><div className="introduccion"><span className="rotulo">Cartagena de Indias</span><h1>El agua en tu barrio</h1><p>Consulta el estado conocido. Cuenta lo que pasa cerca de ti.</p></div><div className="buscar"><label htmlFor="buscar-barrio">Busca tu barrio</label><input id="buscar-barrio" type="search" value={buscar} placeholder="Nombre del barrio" onChange={(evento) => setBuscar(evento.target.value)} /><div className="fila"><span className="pequeno">{lectura.listado ? `${filtrados.length} barrios` : 'Consultando barrios…'}</span><button className="secundario" onClick={() => abrirReporte()}>Reportar</button></div></div>
    {lectura.estado === 'sin-red' && <output className="aviso">Sin conexión. Último listado guardado: {fechaCartagena(lectura.listado?.generadoEn)}.</output>}{lectura.error && <p className="aviso" role="alert">No pudimos actualizar el listado. <button onClick={canal.actualizar}>Reintentar consulta</button></p>}
    {!lectura.listado && !lectura.error && <output className="esqueleto">Consultando el estado del agua…<span /><span /><span /></output>}
    {lectura.listado && <ul className="lista-barrios" aria-label="Barrios y estado del agua" data-listado-listo="true">{filtrados.map((sector) => <li key={sector.id}><Link to="/sectores/$id" params={{ id: sector.id ?? '' }} onClick={() => { ultimoFoco.current = document.activeElement as HTMLElement; setBuscar('') }} aria-current={sector.id === id ? 'true' : undefined}><span className="nombre-lista">{sector.nombre}</span><Estado estado={sector.estado} /><small>Registrado: {fechaCartagena(sector.actualizadoEn)}</small>{verificacionAntigua(sector) && <small>Sin verificación reciente</small>}</Link></li>)}</ul>}
    {lectura.listado && filtrados.length === 0 && <p className="vacio">No encontramos ese barrio. Prueba otro nombre.</p>}
    <p className="generado">Listado generado: {fechaCartagena(lectura.listado?.generadoEn)} · Hora de Cartagena</p></aside>
    <section className="territorio" aria-label="Estado del agua en Cartagena"><div className="barra-mapa"><span className="rotulo">Estado conocido del servicio</span><output>{lectura.estado === 'en-vivo' ? 'En vivo' : lectura.estado === 'sin-red' ? 'Sin conexión' : 'Actualizando conexión'}</output></div><div className="lienzo">{geometria.data && visible ? <><MapaSeguro><Suspense fallback={<p className="mapa-aviso">Preparando el mapa. Los estados ya están en la lista.</p>}><Mapa geometria={geometria.data} sectores={sectores} elegido={id} abrir={abrir} /></Suspense></MapaSeguro><RespuestaMapa geometria={geometria.data} sectores={sectores} /></> : <p className="mapa-aviso">{geometria.isError ? 'No pudimos cargar la geometría. Puedes consultar y reportar desde la lista.' : 'Preparando los barrios. El estado se consulta primero en la lista.'}{geometria.isError && <button onClick={() => { void geometria.refetch() }}>Reintentar mapa</button>}</p>}</div>
    <div className="leyenda" aria-label="Leyenda del mapa">{(['CON_SERVICIO', 'SIN_SERVICIO', 'PRESION_BAJA', 'CORTE_PROGRAMADO', null] as const).map((estado) => <Estado key={estado ?? 'null'} estado={estado} />)}</div></section>
    {id && <Ficha key={id} id={id} actual={sectores.find((sector) => sector.id === id)} cerrar={cerrar} reportar={abrirReporte} />}</main>{errorReporte && <p role="alert" className="aviso-reporte">{errorReporte}</p>}{reporte && <Suspense fallback={<output className="aviso">Preparando el reporte…</output>}><HojaReporte abierto cerrar={() => setReporte(null)} sector={reporte.sector} sectores={sectores} /></Suspense>}</>
}
