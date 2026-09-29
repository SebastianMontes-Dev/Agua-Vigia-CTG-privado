import { Link } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { solicitarCuenta, veredictoDe, type Veredicto } from '../../api/cuentas'
import { validarClaveNueva } from '../../api/panel'
import botones from '../../componentes/Botones.module.css'
import { MarcaRecibido } from '../../componentes/MarcaRecibido'
import { CampoClave } from '../panel/CampoClave'
import estilos from '../panel/Formulario.module.css'
import { mensajeGeneral, useSinReferrer } from './enlace'

export const MENSAJE_SOLICITUD =
  'Recibimos tu solicitud. Si el correo es válido, te enviamos un enlace para verificarlo. Después un administrador tiene que aprobar tu acceso: hasta entonces no podrás ingresar.'

/** `/cuenta/solicitar` (RF042): pide acceso al panel. Responde siempre lo mismo, exista o no ya una cuenta con ese correo. */
export function Solicitar() {
  useSinReferrer()
  const [nombre, setNombre] = useState('')
  const [correo, setCorreo] = useState('')
  const [clave, setClave] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [veredicto, setVeredicto] = useState<Veredicto | null>(null)
  const [local, setLocal] = useState<string | null>(null)
  const problemaClave = clave ? validarClaveNueva(clave) : null

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (enviando) return
    const problema = validarClaveNueva(clave)
    if (problema) { setLocal(problema); return }
    setLocal(null)
    setEnviando(true)
    setVeredicto(veredictoDe(await solicitarCuenta(nombre.trim(), correo.trim(), clave)))
    setEnviando(false)
  }

  if (veredicto?.tipo === 'listo') {
    return (
      <div className={estilos.pagina}>
        <MarcaRecibido />
        <header className={estilos.cabecera}>
          <h1 className={estilos.titular}>Revisa tu correo</h1>
          <output className={estilos.entrada}>{MENSAJE_SOLICITUD}</output>
        </header>
        <nav className={estilos.enlaces} aria-label="Otras opciones">
          <Link to="/panel/ingreso" className={estilos.enlace}>Ir al ingreso</Link>
          <Link to="/" className={estilos.enlace}>Volver al mapa</Link>
        </nav>
      </div>
    )
  }

  const general = veredicto ? mensajeGeneral(veredicto) : null
  const invalido = veredicto?.tipo === 'invalido'
    ? (veredicto.detalle ?? 'Revisa el nombre, el correo y la clave antes de volver a enviarlos.') : null

  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Panel del veedor · solicitud de acceso</p>
        <h1 className={estilos.titular}>Solicita acceso al panel</h1>
        <p className={estilos.entrada}>Verificas tu correo y un administrador aprueba tu cuenta. No hace falta cuenta para ver el mapa ni para reportar.</p>
      </header>
      <form className={estilos.formulario} onSubmit={(evento) => void enviar(evento)}>
        <label className={estilos.campo}>
          Nombre
          <input autoComplete="name" required value={nombre} onChange={(evento) => setNombre(evento.target.value)} />
          <span className={estilos.ayuda}>Es el nombre que aparece en la auditoría del panel.</span>
        </label>
        <label className={estilos.campo}>
          Correo
          <input type="email" autoComplete="email" required value={correo} onChange={(evento) => setCorreo(evento.target.value)} />
        </label>
        <CampoClave etiqueta="Clave" autocompletar="new-password" valor={clave} alCambiar={setClave}
          invalido={problemaClave !== null} ayuda="De 12 a 128 caracteres." />
        {(local ?? problemaClave) && <p className={estilos.ayuda}>{local ?? problemaClave}</p>}
        <Button type="submit" className={botones.principal} isDisabled={enviando}>
          {enviando ? 'Enviando…' : <>Solicitar acceso <span className={botones.flecha} aria-hidden="true">→</span></>}
        </Button>
        {(general ?? invalido) && <p role="alert" className={estilos.aviso}>{general ?? invalido}</p>}
      </form>
      <nav className={estilos.enlaces} aria-label="Otras opciones">
        <Link to="/panel/ingreso" className={estilos.enlace}>Ya tengo cuenta</Link>
        <Link to="/" className={estilos.enlace}>Volver al mapa</Link>
      </nav>
    </div>
  )
}
