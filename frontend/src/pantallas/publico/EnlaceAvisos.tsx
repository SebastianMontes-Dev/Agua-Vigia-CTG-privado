import { Link, useSearch } from '@tanstack/react-router'
import { useLayoutEffect, useState } from 'react'
import { Button } from 'react-aria-components'
import { actuarEnlace, type ResultadoEnlace } from '../../api/avisos'
import { MarcaRecibido } from '../../componentes/MarcaRecibido'
import botones from '../../componentes/Botones.module.css'
import pagina from './Pagina.module.css'
import estilos from './Avisos.module.css'

type Accion = 'confirmar' | 'baja'

function mensajeError(resultado: Exclude<ResultadoEnlace, { tipo: 'completado' }>, accion: Accion): string {
  switch (resultado.tipo) {
    case 'invalido': return accion === 'confirmar'
      ? 'El enlace no es válido o venció. Pide uno nuevo desde Avisos.'
      : 'El enlace no es válido. Revisa el correo o pide ayuda para darte de baja.'
    case 'esperar': return resultado.segundos === null
      ? 'Hay demasiadas solicitudes. Espera antes de volver a intentarlo.'
      : `Hay demasiadas solicitudes. Espera ${resultado.segundos} segundos antes de volver a intentarlo.`
    case 'sin-red': return 'No pudimos comprobar si se recibió la acción. Revisa tu conexión antes de volver a intentarlo.'
    case 'fallo': return 'No pudimos completar la acción. Inténtalo de nuevo más tarde.'
  }
}

const TEXTO: Record<Accion, { titular: string; boton: string; hecho: string; despues: string; siguiente: string }> = {
  confirmar: {
    titular: 'Confirma tus avisos',
    boton: 'Confirmar avisos',
    hecho: 'Avisos confirmados. Te escribiremos cuando cambie el servicio en tus barrios.',
    despues: 'Te escribimos cuando cambie el agua en los barrios que elegiste.',
    siguiente: 'Cada correo trae un enlace para darte de baja cuando quieras.',
  },
  baja: {
    titular: 'Dejar de recibir avisos',
    boton: 'Dejar de recibir avisos',
    hecho: 'Ya no recibirás estos avisos.',
    despues: 'No te llegan más correos de estos barrios.',
    siguiente: 'Puedes volver a pedir avisos cuando quieras desde Avisos.',
  },
}

/** Abrir el enlace del correo nunca ejecuta la acción (plan §6.6): se confirma al pulsar, y el token sale de la URL. */
function ContenidoEnlace({ accion, token }: { accion: Accion; token: string | undefined }) {
  const [tokenInicial] = useState(token)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoEnlace | null>(null)
  const texto = TEXTO[accion]

  useLayoutEffect(() => {
    const url = new URL(window.location.href)
    if (!url.searchParams.has('token')) return
    url.searchParams.delete('token')
    window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`)
  }, [])

  async function actuar() {
    if (!tokenInicial || enviando) return
    setEnviando(true)
    setResultado(await actuarEnlace(accion, tokenInicial))
    setEnviando(false)
  }

  return (
    <div className={`${pagina.pagina} ${estilos.pagina}`}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Avisos por correo</p>
        <h1 className={`${pagina.titular} ${estilos.titular}`}>{texto.titular}</h1>
      </header>
      <div className={estilos.columnas}>
        <section className={estilos.tarea} aria-label={texto.titular}>
          {!tokenInicial ? (
            <>
              <p role="alert">Falta el enlace completo. Abre el que llegó a tu correo.</p>
              <Link to="/avisos" className={botones.secundario}>Pedir avisos</Link>
            </>
          ) : resultado?.tipo === 'completado' ? (
            <>
              <MarcaRecibido />
              <output className={estilos.respuesta}>{texto.hecho}</output>
              <Link to="/" className={botones.secundario}>Ver el mapa</Link>
            </>
          ) : (
            <>
              <p className={estilos.nota}>Pulsa el botón para terminar. Abrir el enlace no cambia nada por sí solo.</p>
              <Button className={botones.principal} isDisabled={enviando} onPress={() => void actuar()}>
                {enviando ? 'Enviando…' : <>{texto.boton} <span className={botones.flecha} aria-hidden="true">→</span></>}
              </Button>
              {resultado && <p role="alert" className={estilos.alerta}>{mensajeError(resultado, accion)}</p>}
              {resultado?.tipo === 'invalido' && <Link to="/avisos" className={estilos.enlace}>Pedir un enlace nuevo</Link>}
            </>
          )}
        </section>
        <aside className={estilos.explicacion} aria-labelledby="titulo-despues">
          <h2 id="titulo-despues">{accion === 'confirmar' ? 'Al confirmar' : 'Al darte de baja'}</h2>
          <ol className={estilos.pasos}>
            <li><strong>{texto.despues}</strong></li>
            <li><strong>{texto.siguiente}</strong></li>
          </ol>
        </aside>
      </div>
    </div>
  )
}

export function ConfirmarAvisos() {
  const { token } = useSearch({ from: '/publico/avisos/confirmar' })
  return <ContenidoEnlace accion="confirmar" token={token} />
}

export function BajaAvisos() {
  const { token } = useSearch({ from: '/publico/avisos/baja' })
  return <ContenidoEnlace accion="baja" token={token} />
}
