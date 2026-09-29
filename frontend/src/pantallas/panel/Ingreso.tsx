import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { iniciarSesion, type ResultadoIngreso } from '../../api/panel'
import botones from '../../componentes/Botones.module.css'
import { formatearNumero } from '../../dominio/formato'
import { CampoClave } from './CampoClave'
import estilos from './Formulario.module.css'

const MOTIVOS: Record<string, string> = {
  terminada: 'Tu sesión terminó. Ingresa de nuevo.',
  'clave-cambiada': 'Cambiaste tu clave y cerramos tus sesiones. Ingresa con la clave nueva.',
  'segundo-factor-desactivado': 'Desactivaste el segundo factor y cerramos tus sesiones. Ingresa solo con tu clave.',
  'clave-restablecida': 'Guardamos tu clave nueva y cerramos tus sesiones. Ingresa con ella.',
}

/** Un `type` decide la reacción (`docs/api/cuentas-y-sesion.md`); nunca se distingue si falló el correo o la clave. */
export function mensajeDeIngreso(resultado: ResultadoIngreso, conCodigo: boolean): { texto: string; urgente: boolean } | null {
  switch (resultado.tipo) {
    case 'entro': return null
    case 'pide-codigo': return { texto: 'Escribe el código de seis dígitos de tu app de autenticación.', urgente: false }
    case 'credencial-invalida':
      return { texto: conCodigo ? 'El correo, la clave o el código no son correctos.' : 'El correo o la clave no son correctos.', urgente: true }
    case 'cuenta-no-habilitada': return { texto: resultado.mensaje, urgente: true }
    case 'bloqueada': return {
      texto: resultado.segundos === null
        ? 'Hubo demasiados intentos fallidos. Espera unos minutos antes de volver a intentarlo.'
        : `Hubo demasiados intentos fallidos. Espera ${formatearNumero(Math.max(1, Math.ceil(resultado.segundos / 60)))} min antes de volver a intentarlo.`,
      urgente: true,
    }
    case 'esperar': return {
      texto: resultado.segundos === null
        ? 'Hay demasiados intentos desde esta conexión. Espera unos minutos.'
        : `Hay demasiados intentos desde esta conexión. Espera ${formatearNumero(Math.max(1, Math.ceil(resultado.segundos / 60)))} min.`,
      urgente: true,
    }
    case 'sin-red': return { texto: 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.', urgente: true }
    case 'fallo': return { texto: 'Algo falló de nuestro lado. Inténtalo otra vez en un momento.', urgente: true }
  }
}

/** Ingreso al panel: correo y clave; si la cuenta tiene segundo factor, el mismo formulario pide el código. */
export function Ingreso() {
  const { motivo } = useSearch({ strict: false }) as { motivo?: string }
  const navegar = useNavigate()
  const [correo, setCorreo] = useState('')
  const [clave, setClave] = useState('')
  const [codigo, setCodigo] = useState('')
  const [pideCodigo, setPideCodigo] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoIngreso | null>(null)
  const campoCodigo = useRef<HTMLInputElement>(null)

  useEffect(() => { if (pideCodigo) campoCodigo.current?.focus() }, [pideCodigo])

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (enviando) return
    setEnviando(true)
    const respuesta = await iniciarSesion(correo.trim(), clave, pideCodigo ? codigo.trim() : undefined)
    setEnviando(false)
    if (respuesta.tipo === 'entro') {
      void navegar({ to: respuesta.alcance === 'COMPLETO' ? '/panel' : '/panel/segundo-factor', replace: true })
      return
    }
    if (respuesta.tipo === 'pide-codigo') setPideCodigo(true)
    if (respuesta.tipo === 'credencial-invalida') setCodigo('')
    setResultado(respuesta)
  }

  const mensaje = resultado ? mensajeDeIngreso(resultado, pideCodigo) : null

  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Panel del veedor · solo cuentas autorizadas</p>
        <h1 className={estilos.titular}>Ingresa para revisar reportes y cortes</h1>
        {motivo && MOTIVOS[motivo] && !mensaje && <output className={estilos.aviso}>{MOTIVOS[motivo]}</output>}
      </header>

      <form className={estilos.formulario} onSubmit={(evento) => void enviar(evento)}>
        <label className={estilos.campo}>
          Correo
          <input type="email" autoComplete="username" required value={correo} readOnly={pideCodigo}
            onChange={(evento) => setCorreo(evento.target.value)} />
        </label>
        <CampoClave etiqueta="Clave" autocompletar="current-password" valor={clave} alCambiar={setClave} />
        {pideCodigo && (
          <label className={estilos.campo}>
            Código de tu app de autenticación
            <input ref={campoCodigo} className={estilos.codigo} inputMode="numeric" autoComplete="one-time-code"
              pattern="[0-9]{6}" maxLength={6} required value={codigo}
              onChange={(evento) => setCodigo(evento.target.value.replace(/\D/g, ''))} />
          </label>
        )}
        <Button type="submit" className={botones.principal} isDisabled={enviando}>
          {enviando ? 'Ingresando…' : <>Ingresar <span className={botones.flecha} aria-hidden="true">→</span></>}
        </Button>
        {mensaje && (mensaje.urgente
          ? <p role="alert" className={estilos.aviso}>{mensaje.texto}</p>
          : <output className={estilos.aviso}>{mensaje.texto}</output>)}
      </form>

      <nav className={estilos.enlaces} aria-label="Otras opciones">
        <Link to="/cuenta/olvide" className={estilos.enlace}>Olvidé mi clave</Link>
        <Link to="/cuenta/solicitar" className={estilos.enlace}>Solicitar acceso</Link>
        <Link to="/" className={estilos.enlace}>Volver al mapa</Link>
      </nav>
    </div>
  )
}
