import { Link, useSearch } from '@tanstack/react-router'
import { useLayoutEffect, useState } from 'react'
import { Button } from 'react-aria-components'
import { actuarEnlace, type ResultadoEnlace } from '../../api/avisos'
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

function ContenidoEnlace({ accion, token }: { accion: Accion; token: string | undefined }) {
  const [tokenInicial] = useState(token)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoEnlace | null>(null)
  const confirmacion = accion === 'confirmar'

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

  return <div className={`${pagina.pagina} ${estilos.enlacePagina}`}>
    <span className={estilos.rotulo}>AVISOS POR CORREO</span>
    <h1 className={pagina.titular}>{confirmacion ? 'Confirma tus avisos' : 'Dejar de recibir avisos'}</h1>
    {!tokenInicial ? <>
      <p role="alert">Falta el enlace completo. Abre el que llegó a tu correo.</p>
      <Link to="/avisos" className={pagina.enlace}>Pedir avisos</Link>
    </> : resultado?.tipo === 'completado' ? <>
      <output className={pagina.entrada}>{confirmacion
        ? 'Avisos confirmados. Te escribiremos cuando cambie el servicio en tus barrios.'
        : 'Ya no recibirás estos avisos.'}</output>
      <Link to="/" className={botones.secundario}>Ver el mapa</Link>
    </> : <>
      <p className={pagina.entrada}>{confirmacion
        ? 'Hasta que confirmes, no recibirás avisos. El enlace vence 48 horas después de solicitarlo.'
        : 'Al darte de baja, dejaremos de enviarte avisos y eliminaremos tu correo de la suscripción.'}</p>
      <Button className={botones.principal} isDisabled={enviando} onPress={() => void actuar()}>
        {enviando ? 'Enviando…' : confirmacion ? 'Confirmar avisos' : 'Dejar de recibir avisos'}
      </Button>
      {resultado && <p role="alert">{mensajeError(resultado, accion)}</p>}
      {resultado?.tipo === 'invalido' && <Link to="/avisos" className={pagina.enlace}>Pedir un enlace nuevo</Link>}
    </>}
  </div>
}

export function ConfirmarAvisos() {
  const { token } = useSearch({ from: '/publico/avisos/confirmar' })
  return <ContenidoEnlace accion="confirmar" token={token} />
}

export function BajaAvisos() {
  const { token } = useSearch({ from: '/publico/avisos/baja' })
  return <ContenidoEnlace accion="baja" token={token} />
}
