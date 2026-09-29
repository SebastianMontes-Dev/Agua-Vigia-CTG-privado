import { Link, useSearch } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { reenviarVerificacion, verificarCorreo, veredictoDe, type Veredicto } from '../../api/cuentas'
import botones from '../../componentes/Botones.module.css'
import { MarcaRecibido } from '../../componentes/MarcaRecibido'
import estilos from '../panel/Formulario.module.css'
import { mensajeGeneral, useSinReferrer, useTokenDeEnlace } from './enlace'

export const MENSAJE_VERIFICADO = 'Correo verificado. Tu acceso está pendiente de aprobación: un administrador tiene que aprobarlo antes de que puedas ingresar.'
export const MENSAJE_REENVIO = 'Si hay una solicitud pendiente con ese correo, te reenviamos el enlace. Solo lo enviamos una vez cada 2 minutos.'

/** `/cuenta/verificar`: abrir el enlace no verifica nada; se verifica al pulsar el botón, y el token sale de la URL. */
export function Verificar() {
  useSinReferrer()
  const { token: enUrl } = useSearch({ strict: false }) as { token?: string }
  const token = useTokenDeEnlace(enUrl)
  const [enviando, setEnviando] = useState(false)
  const [veredicto, setVeredicto] = useState<Veredicto | null>(null)
  const [correo, setCorreo] = useState('')
  const [reenvio, setReenvio] = useState<Veredicto | null>(null)

  async function verificar() {
    if (!token || enviando) return
    setEnviando(true)
    setVeredicto(veredictoDe(await verificarCorreo(token)))
    setEnviando(false)
  }

  async function reenviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setReenvio(veredictoDe(await reenviarVerificacion(correo.trim())))
  }

  if (veredicto?.tipo === 'listo') {
    return (
      <div className={estilos.pagina}>
        <MarcaRecibido />
        <header className={estilos.cabecera}>
          <h1 className={estilos.titular}>Correo verificado</h1>
          <output className={estilos.entrada}>{MENSAJE_VERIFICADO}</output>
        </header>
        <nav className={estilos.enlaces} aria-label="Otras opciones">
          <Link to="/panel/ingreso" className={estilos.enlace}>Ir al ingreso</Link>
          <Link to="/" className={estilos.enlace}>Volver al mapa</Link>
        </nav>
      </div>
    )
  }

  const vencido = !token || veredicto?.tipo === 'invalido'
  const general = veredicto ? mensajeGeneral(veredicto) : null
  const generalReenvio = reenvio ? mensajeGeneral(reenvio) : null

  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Panel del veedor · solicitud de acceso</p>
        <h1 className={estilos.titular}>Verifica tu correo</h1>
        <p className={estilos.entrada}>Confirma que este correo es tuyo. Después, un administrador aprueba tu acceso.</p>
      </header>
      {token && veredicto?.tipo !== 'invalido' && (
        <Button className={botones.principal} isDisabled={enviando} onPress={() => void verificar()}>
          {enviando ? 'Verificando…' : <>Verificar mi correo <span className={botones.flecha} aria-hidden="true">→</span></>}
        </Button>
      )}
      {general && <p role="alert" className={estilos.aviso}>{general}</p>}
      {vencido && (
        <section className={estilos.seccion} aria-labelledby="titulo-reenvio">
          <h2 id="titulo-reenvio">{token ? 'Este enlace ya se usó o venció' : 'Falta el enlace de verificación'}</h2>
          <p className={estilos.nota}>Pide otro: el anterior deja de servir. Escribe el correo con el que solicitaste acceso.</p>
          <form className={estilos.formulario} onSubmit={(evento) => void reenviar(evento)}>
            <label className={estilos.campo}>
              Correo
              <input type="email" autoComplete="email" required value={correo} onChange={(evento) => setCorreo(evento.target.value)} />
            </label>
            <Button type="submit" className={botones.secundario}>Reenviar el enlace</Button>
            {reenvio?.tipo === 'listo' && <output className={estilos.aviso}>{MENSAJE_REENVIO}</output>}
            {generalReenvio && <p role="alert" className={estilos.aviso}>{generalReenvio}</p>}
          </form>
        </section>
      )}
      <nav className={estilos.enlaces} aria-label="Otras opciones">
        <Link to="/panel/ingreso" className={estilos.enlace}>Ir al ingreso</Link>
        <Link to="/" className={estilos.enlace}>Volver al mapa</Link>
      </nav>
    </div>
  )
}
