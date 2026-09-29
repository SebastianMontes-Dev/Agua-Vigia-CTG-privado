import { Link } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { pedirRestablecimiento, veredictoDe, type Veredicto } from '../../api/cuentas'
import botones from '../../componentes/Botones.module.css'
import { MarcaRecibido } from '../../componentes/MarcaRecibido'
import estilos from '../panel/Formulario.module.css'
import { mensajeGeneral } from './enlace'

/** Idéntico exista o no la cuenta: si respondiera distinto, cualquiera podría averiguar qué correos tienen cuenta. */
export const MENSAJE_OLVIDE = 'Si hay una cuenta con ese correo, te enviamos un enlace para elegir una clave nueva. El enlace vence en 30 minutos.'

/** `/cuenta/olvide`: pide el enlace para restablecer la clave. */
export function Olvide() {
  const [correo, setCorreo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [veredicto, setVeredicto] = useState<Veredicto | null>(null)

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (enviando) return
    setEnviando(true)
    setVeredicto(veredictoDe(await pedirRestablecimiento(correo.trim())))
    setEnviando(false)
  }

  if (veredicto?.tipo === 'listo') {
    return (
      <div className={estilos.pagina}>
        <MarcaRecibido />
        <header className={estilos.cabecera}>
          <h1 className={estilos.titular}>Revisa tu correo</h1>
          <output className={estilos.entrada}>{MENSAJE_OLVIDE}</output>
        </header>
        <nav className={estilos.enlaces} aria-label="Otras opciones">
          <Link to="/panel/ingreso" className={estilos.enlace}>Volver al ingreso</Link>
        </nav>
      </div>
    )
  }

  const general = veredicto ? mensajeGeneral(veredicto) : null
  const invalido = veredicto?.tipo === 'invalido' ? 'Revisa el correo antes de volver a enviarlo.' : null

  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Panel del veedor · recuperar la clave</p>
        <h1 className={estilos.titular}>Recupera tu clave</h1>
        <p className={estilos.entrada}>Escribe el correo de tu cuenta y te enviamos un enlace para elegir una clave nueva.</p>
      </header>
      <form className={estilos.formulario} onSubmit={(evento) => void enviar(evento)}>
        <label className={estilos.campo}>
          Correo
          <input type="email" autoComplete="email" required value={correo} onChange={(evento) => setCorreo(evento.target.value)} />
        </label>
        <Button type="submit" className={botones.principal} isDisabled={enviando}>
          {enviando ? 'Enviando…' : <>Enviar enlace <span className={botones.flecha} aria-hidden="true">→</span></>}
        </Button>
        {(general ?? invalido) && <p role="alert" className={estilos.aviso}>{general ?? invalido}</p>}
      </form>
      <nav className={estilos.enlaces} aria-label="Otras opciones">
        <Link to="/panel/ingreso" className={estilos.enlace}>Volver al ingreso</Link>
        <Link to="/" className={estilos.enlace}>Volver al mapa</Link>
      </nav>
    </div>
  )
}
