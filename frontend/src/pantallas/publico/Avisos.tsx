import { Link, useSearch } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, normalizarError } from '../../api/cliente'
import { MENSAJE_NEUTRO, pedirAvisos, type ResultadoAlta } from '../../api/avisos'
import { SelectorBarrio } from '../../componentes/SelectorBarrio'
import botones from '../../componentes/Botones.module.css'
import { nombreLegible, type Sector } from '../../dominio/sectores'
import { formatearNumero } from '../../dominio/formato'
import pagina from './Pagina.module.css'
import estilos from './Avisos.module.css'

function usarBarrios() {
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

export function Avisos() {
  const { sector } = useSearch({ from: '/publico/avisos' })
  const barrios = usarBarrios()
  const [correo, setCorreo] = useState('')
  const [elegidos, setElegidos] = useState<string[]>(sector ? [sector] : [])
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoAlta | null>(null)
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  const disponibles = barrios.data ?? []
  const elegidosValidos = elegidos.filter((id) => disponibles.some((barrio) => barrio.id === id))
  const barrioDesconocido = barrios.isSuccess && elegidosValidos.length !== elegidos.length

  async function enviar(evento: React.FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (enviando) return
    if (!correo.trim() || elegidosValidos.length === 0 || barrioDesconocido) {
      setErrorLocal('Escribe tu correo y elige al menos un barrio de la lista.')
      return
    }
    setErrorLocal(null)
    setEnviando(true)
    setResultado(await pedirAvisos(correo.trim(), elegidosValidos))
    setEnviando(false)
  }

  return (
    <div className={`${pagina.pagina} ${estilos.pagina}`}>
      <div className={estilos.columnas}>
        <section className={estilos.tarea} aria-labelledby="titulo-avisos">
          <span className={estilos.rotulo}>AVISOS POR CORREO</span>
          <h1 id="titulo-avisos" className={pagina.titular}>Recibe avisos del agua en tus barrios</h1>
          <form className={estilos.formulario} onSubmit={(evento) => void enviar(evento)}>
            <label className={estilos.campo}>
              <span>Correo electrónico</span>
              <input type="email" autoComplete="email" required value={correo} onChange={(evento) => setCorreo(evento.target.value)} placeholder="tu@correo.com" />
            </label>
            {barrios.isLoading ? <output>Cargando barrios…</output> : barrios.isError ? (
              <p role="alert">No pudimos cargar los barrios. <button type="button" className={estilos.enlace} onClick={() => void barrios.refetch()}>Volver a intentar</button></p>
            ) : <>
              <SelectorBarrio key={elegidosValidos.join(',')} sectores={disponibles.filter((barrio) => !elegidosValidos.includes(barrio.id ?? ''))}
                etiqueta="Busca un barrio para recibir avisos" alCambiar={(id) => {
                  if (id && !elegidos.includes(id)) setElegidos([...elegidosValidos, id])
                }} />
              {elegidosValidos.length > 0 && <ul className={estilos.elegidos} aria-label="Barrios elegidos">
                {elegidosValidos.map((id) => <li key={id}>
                  <button type="button" aria-label={`Quitar ${nombreLegible(disponibles.find((barrio) => barrio.id === id)?.nombre)}`} onClick={() => setElegidos(elegidosValidos.filter((elegido) => elegido !== id))}>
                    {nombreLegible(disponibles.find((barrio) => barrio.id === id)?.nombre)} <span aria-hidden="true">×</span>
                  </button>
                </li>)}
              </ul>}
              {barrioDesconocido && <p role="alert">El barrio del enlace no está disponible. Elige uno de la lista.</p>}
            </>}
            {errorLocal && <p role="alert">{errorLocal}</p>}
            <button type="submit" className={botones.principal} disabled={enviando || !barrios.isSuccess}>
              {enviando ? 'Enviando…' : <>Enviar enlace de confirmación <span className={botones.flecha} aria-hidden="true">→</span></>}
            </button>
            {resultado && <output className={estilos.respuesta}>
              <strong>{MENSAJE_NEUTRO}</strong>
              {mensajeAdicional(resultado) && <p>{mensajeAdicional(resultado)}</p>}
            </output>}
          </form>
        </section>
        <aside className={estilos.explicacion} aria-label="Antes y después de confirmar">
          <h2>Antes y después de confirmar</h2>
          <p><strong>Antes.</strong> Recibes un enlace, no avisos.</p>
          <p><strong>Después.</strong> Te avisamos si cambia el agua.</p>
          <details className={estilos.detalles}>
            <summary>Cómo funciona</summary>
            <p>El enlace vence en 48 horas. Cada aviso trae un enlace para darte de baja.</p>
            {botValido && <a href={`https://t.me/${botValido}`} target="_blank" rel="noreferrer">Recibir avisos por Telegram</a>}
            <Link to="/" className={pagina.enlace}>Ver el mapa</Link>
          </details>
        </aside>
      </div>
    </div>
  )
}
