import { Link, useSearch } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { api, normalizarError } from '../../api/cliente'
import { MENSAJE_NEUTRO, pedirAvisos, type ResultadoAlta } from '../../api/avisos'
import { GlifoEstado } from '../../componentes/GlifoEstado'
import { MarcaRecibido } from '../../componentes/MarcaRecibido'
import { SelectorBarrio } from '../../componentes/SelectorBarrio'
import botones from '../../componentes/Botones.module.css'
import { presentarEstado, type EstadoServicio } from '../../dominio/estados'
import { formatearNumero } from '../../dominio/formato'
import { nombreLegible, type Sector } from '../../dominio/sectores'
import pagina from './Pagina.module.css'
import estilos from './Avisos.module.css'

/** Un GET puntual y no `useListado()`: el listado abriría el canal en vivo en una página que no muestra el mapa. */
function useBarrios() {
  return useQuery({
    queryKey: ['barrios-avisos'],
    queryFn: async () => {
      const { data, error, response } = await api.GET('/api/sectores').catch(() => ({ data: undefined, error: undefined, response: null }))
      if (!response?.ok || !data) throw normalizarError(response ?? null, error)
      return data.sectores as Sector[]
    },
    staleTime: 60_000,
  })
}

function mensajeAdicional(resultado: ResultadoAlta): string | null {
  switch (resultado.tipo) {
    case 'recibido': return null
    case 'invalido': return 'Revisa el correo y los barrios elegidos antes de volver a enviarlo.'
    case 'esperar': return resultado.segundos === null
      ? 'Hay demasiadas solicitudes. Espera antes de volver a intentarlo.'
      : `Hay demasiadas solicitudes. Espera ${formatearNumero(resultado.segundos)} segundos antes de volver a intentarlo.`
    case 'sin-red': return 'No pudimos comprobar si se recibió la solicitud. Revisa tu conexión antes de volver a enviarla.'
    case 'fallo': return 'No pudimos comprobar si se recibió la solicitud. Inténtalo de nuevo más tarde.'
  }
}

const bot = import.meta.env.VITE_TELEGRAM_BOT?.trim().replace(/^@/, '')
const botValido = bot && /^[a-zA-Z0-9_]{5,32}$/.test(bot) ? bot : null

function estadoDe(sector: Sector | undefined): EstadoServicio | null {
  const estado = sector?.estado
  return estado === 'CON_SERVICIO' || estado === 'SIN_SERVICIO' || estado === 'PRESION_BAJA' || estado === 'CORTE_PROGRAMADO' ? estado : null
}

/** `identidad.md` §4.1: los pasos se leen de un vistazo; el detalle de cada uno cabe en una línea. */
export function ComoFunciona() {
  return (
    <aside className={estilos.explicacion} aria-labelledby="titulo-como-funciona">
      <h2 id="titulo-como-funciona">Así funciona</h2>
      <ol className={estilos.pasos}>
        <li><strong>Eliges tus barrios</strong><span>Sin cuenta y sin contraseña.</span></li>
        <li><strong>Confirmas desde el correo</strong><span>Hasta entonces no te llega ningún aviso. El enlace vence en 48 horas.</span></li>
        <li><strong>Te escribimos cuando cambie el agua</strong><span>Cada correo trae un enlace para darte de baja.</span></li>
      </ol>
      {botValido && <a href={`https://t.me/${botValido}`} target="_blank" rel="noreferrer" className={estilos.enlace}>También por Telegram</a>}
    </aside>
  )
}

/** RF012–RF015 · `ADR-079`: los barrios van primero y muestran su estado de ahora; el correo, después. */
export function Avisos() {
  const { sector } = useSearch({ from: '/publico/avisos' })
  const barrios = useBarrios()
  const [correo, setCorreo] = useState('')
  const [elegidos, setElegidos] = useState<string[]>(sector ? [sector] : [])
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoAlta | null>(null)
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  const disponibles = barrios.data ?? []
  const porId = new Map(disponibles.map((barrio) => [barrio.id, barrio]))
  const elegidosValidos = elegidos.filter((id) => porId.has(id))
  const barrioDesconocido = barrios.isSuccess && elegidosValidos.length !== elegidos.length
  const nombreDe = (id: string) => nombreLegible(porId.get(id)?.nombre)

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (enviando) return
    if (!correo.trim() || elegidosValidos.length === 0 || barrioDesconocido) {
      setErrorLocal(elegidosValidos.length === 0 ? 'Elige al menos un barrio de la lista.' : 'Escribe tu correo.')
      return
    }
    setErrorLocal(null)
    setEnviando(true)
    setResultado(await pedirAvisos(correo.trim(), elegidosValidos))
    setEnviando(false)
  }

  function otraSolicitud() {
    setResultado(null)
    setCorreo('')
  }

  const recibido = resultado?.tipo === 'recibido'

  return (
    <div className={`${pagina.pagina} ${estilos.pagina}`}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Avisos por correo · gratis y sin cuenta</p>
        <h1 className={`${pagina.titular} ${estilos.titular}`}>Te escribimos cuando cambie el agua en tu barrio</h1>
      </header>

      <div className={estilos.columnas}>
        {recibido ? (
          <section className={estilos.tarea} aria-labelledby="titulo-recibido">
            <MarcaRecibido />
            <h2 id="titulo-recibido" className={estilos.subtitulo}>Revisa tu correo</h2>
            <output className={estilos.respuesta}>{MENSAJE_NEUTRO}.</output>
            <p className={estilos.nota}>Pediste avisos de {elegidosValidos.map(nombreDe).join(', ')}.</p>
            <div className={estilos.acciones}>
              <button type="button" className={estilos.enlace} onClick={otraSolicitud}>Usar otro correo</button>
              <Link to="/" className={estilos.enlace}>Ver el mapa</Link>
            </div>
          </section>
        ) : (
          <form className={estilos.tarea} onSubmit={(evento) => void enviar(evento)} noValidate>
            <fieldset className={estilos.grupo}>
              <legend>Tus barrios</legend>
              {barrios.isPending ? <output className={estilos.nota}>Cargando barrios…</output> : barrios.isError ? (
                <p role="alert">No pudimos cargar los barrios. <button type="button" className={estilos.enlace} onClick={() => void barrios.refetch()}>Volver a intentar</button></p>
              ) : (
                <>
                  {elegidosValidos.length > 0 && (
                    <ul className={estilos.elegidos} aria-label="Barrios elegidos">
                      {elegidosValidos.map((id) => {
                        const estado = estadoDe(porId.get(id))
                        return (
                          <li key={id}>
                            <span className={estilos.barrio}>{nombreDe(id)}</span>
                            <span className={estilos.estado}>
                              {estado ? <><GlifoEstado estado={estado} tamano={16} />{presentarEstado(estado).texto}</> : 'Sin datos verificados'}
                            </span>
                            <button type="button" className={estilos.quitar} aria-label={`Quitar ${nombreDe(id)}`}
                              onClick={() => setElegidos(elegidosValidos.filter((elegido) => elegido !== id))}>Quitar</button>
                          </li>
                        )
                      })}
                    </ul>
                  )}
                  <SelectorBarrio key={elegidosValidos.join(',')}
                    sectores={disponibles.filter((barrio) => !elegidosValidos.includes(barrio.id ?? ''))}
                    etiqueta={elegidosValidos.length ? 'Agregar otro barrio' : 'Busca tu barrio'}
                    alCambiar={(id) => { if (id && !elegidos.includes(id)) setElegidos([...elegidosValidos, id]) }} />
                  {barrioDesconocido && <p role="alert">El barrio del enlace no está disponible. Elige uno de la lista.</p>}
                </>
              )}
            </fieldset>
            <label className={estilos.campo}>
              Tu correo
              <input type="email" autoComplete="email" required value={correo} onChange={(evento) => setCorreo(evento.target.value)} />
            </label>
            <Button type="submit" className={botones.principal} isDisabled={enviando || !barrios.isSuccess}>
              {enviando ? 'Enviando…' : <>Enviar enlace de confirmación <span className={botones.flecha} aria-hidden="true">→</span></>}
            </Button>
            {errorLocal && <p role="alert" className={estilos.alerta}>{errorLocal}</p>}
            {resultado && (
              <output className={estilos.alerta}>
                {MENSAJE_NEUTRO}.{mensajeAdicional(resultado) && <> {mensajeAdicional(resultado)}</>}
              </output>
            )}
          </form>
        )}
        <ComoFunciona />
      </div>
    </div>
  )
}
