import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { fijarClaveNueva, veredictoDe, type Veredicto } from '../../api/cuentas'
import { validarClaveNueva } from '../../api/panel'
import botones from '../../componentes/Botones.module.css'
import { CampoClave } from '../panel/CampoClave'
import estilos from '../panel/Formulario.module.css'
import { mensajeGeneral, useSinReferrer, useTokenDeEnlace } from './enlace'

/** `/cuenta/restablecer`: fija la clave nueva con el token del correo. Restablecer cierra todas las sesiones de la cuenta. */
export function Restablecer() {
  useSinReferrer()
  const navegar = useNavigate()
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
    const resultado = veredictoDe(await fijarClaveNueva(token, clave))
    setEnviando(false)
    if (resultado.tipo === 'listo') {
      void navegar({ to: '/panel/ingreso', search: { motivo: 'clave-restablecida' }, replace: true })
      return
    }
    setVeredicto(resultado)
  }

  const sinEnlace = !token || veredicto?.tipo === 'invalido' && veredicto.detalle === null
  const general = veredicto ? mensajeGeneral(veredicto) : null
  const detalle = veredicto?.tipo === 'invalido' ? veredicto.detalle : null

  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Panel del veedor · recuperar la clave</p>
        <h1 className={estilos.titular}>Elige tu clave nueva</h1>
        <p className={estilos.entrada}>Al guardarla cerramos todas tus sesiones abiertas y te pedimos ingresar de nuevo.</p>
      </header>
      {sinEnlace ? (
        <section className={estilos.seccion} aria-labelledby="titulo-enlace">
          <h2 id="titulo-enlace">{token ? 'Este enlace ya se usó o venció' : 'Falta el enlace para restablecer la clave'}</h2>
          <p className={estilos.nota}>Los enlaces duran 30 minutos y solo sirven una vez. Pide otro y usa el más reciente.</p>
          <Link to="/cuenta/olvide" className={botones.secundario}>Pedir otro enlace</Link>
        </section>
      ) : (
        <form className={estilos.formulario} onSubmit={(evento) => void enviar(evento)}>
          <CampoClave etiqueta="Clave nueva" autocompletar="new-password" valor={clave} alCambiar={setClave}
            invalido={problemaClave !== null} ayuda="De 12 a 128 caracteres." />
          {(local ?? problemaClave) && <p className={estilos.ayuda}>{local ?? problemaClave}</p>}
          <Button type="submit" className={botones.principal} isDisabled={enviando}>
            {enviando ? 'Guardando…' : <>Guardar clave <span className={botones.flecha} aria-hidden="true">→</span></>}
          </Button>
          {(general ?? detalle) && <p role="alert" className={estilos.aviso}>{general ?? detalle}</p>}
        </form>
      )}
      <nav className={estilos.enlaces} aria-label="Otras opciones">
        <Link to="/panel/ingreso" className={estilos.enlace}>Volver al ingreso</Link>
      </nav>
    </div>
  )
}
