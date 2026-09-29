import { Link, useSearch } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { aceptarInvitacion, veredictoDe, type Veredicto } from '../../api/cuentas'
import { validarClaveNueva } from '../../api/panel'
import botones from '../../componentes/Botones.module.css'
import { MarcaRecibido } from '../../componentes/MarcaRecibido'
import { CampoClave } from '../panel/CampoClave'
import estilos from '../panel/Formulario.module.css'
import { mensajeGeneral, useSinReferrer, useTokenDeEnlace } from './enlace'

/**
 * `/cuenta/invitacion` (RF043): la persona fija su clave y la cuenta queda activa. El token es opaco: no dice el nombre, el
 * correo ni el rol, así que la pantalla no los inventa.
 */
export function Invitacion() {
  useSinReferrer()
  const { token: enUrl } = useSearch({ strict: false }) as { token?: string }
  const token = useTokenDeEnlace(enUrl)
  const [clave, setClave] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [veredicto, setVeredicto] = useState<Veredicto | null>(null)
  const [local, setLocal] = useState<string | null>(null)
  const problemaClave = clave ? validarClaveNueva(clave) : null

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (!token || enviando) return
    const problema = validarClaveNueva(clave)
    if (problema) { setLocal(problema); return }
    setLocal(null)
    setEnviando(true)
    setVeredicto(veredictoDe(await aceptarInvitacion(token, clave)))
    setEnviando(false)
  }

  if (veredicto?.tipo === 'listo') {
    return (
      <div className={estilos.pagina}>
        <MarcaRecibido />
        <header className={estilos.cabecera}>
          <h1 className={estilos.titular}>Tu cuenta está activa</h1>
          <output className={estilos.entrada}>Guardamos tu clave. Ya puedes ingresar al panel con tu correo y esa clave.</output>
        </header>
        <Link to="/panel/ingreso" className={botones.secundario}>Ir al ingreso</Link>
      </div>
    )
  }

  const sinEnlace = !token || veredicto?.tipo === 'invalido' && veredicto.detalle === null
  const general = veredicto ? mensajeGeneral(veredicto) : null
  const detalle = veredicto?.tipo === 'invalido' ? veredicto.detalle : null

  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Panel del veedor · invitación</p>
        <h1 className={estilos.titular}>Acepta tu invitación</h1>
        <p className={estilos.entrada}>Elige la clave con la que vas a ingresar. Al guardarla, tu cuenta queda activa sin otra aprobación.</p>
      </header>
      {sinEnlace ? (
        <section className={estilos.seccion} aria-labelledby="titulo-invitacion">
          <h2 id="titulo-invitacion">{token ? 'Esta invitación ya se usó o venció' : 'Falta el enlace de la invitación'}</h2>
          <p className={estilos.nota}>Pide a la persona que te invitó que te envíe otra. Los enlaces de invitación duran 7 días y solo sirven una vez.</p>
        </section>
      ) : (
        <form className={estilos.formulario} onSubmit={(evento) => void enviar(evento)}>
          <CampoClave etiqueta="Clave" autocompletar="new-password" valor={clave} alCambiar={setClave}
            invalido={problemaClave !== null} ayuda="De 12 a 128 caracteres." />
          {(local ?? problemaClave) && <p className={estilos.ayuda}>{local ?? problemaClave}</p>}
          <Button type="submit" className={botones.principal} isDisabled={enviando}>
            {enviando ? 'Guardando…' : <>Aceptar invitación <span className={botones.flecha} aria-hidden="true">→</span></>}
          </Button>
          {(general ?? detalle) && <p role="alert" className={estilos.aviso}>{general ?? detalle}</p>}
        </form>
      )}
      <nav className={estilos.enlaces} aria-label="Otras opciones">
        <Link to="/panel/ingreso" className={estilos.enlace}>Ir al ingreso</Link>
        <Link to="/" className={estilos.enlace}>Volver al mapa</Link>
      </nav>
    </div>
  )
}
