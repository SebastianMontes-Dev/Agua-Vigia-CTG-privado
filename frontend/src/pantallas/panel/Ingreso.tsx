import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { Redirigir } from '../../app/Redirigir'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { iniciarSesion, type ResultadoIngreso } from '../../api/panel'
import { useAlcance } from '../../app/panel'
import { mensajeIngreso } from '../../dominio/mensajes-panel'
import botones from '../../componentes/Botones.module.css'
import pagina from '../publico/Pagina.module.css'
import estilos from './Formulario.module.css'

const MOTIVO: Record<'vencida' | 'cerrada', string> = {
  vencida: 'Tu sesión terminó. Ingresa de nuevo.',
  cerrada: 'Cerraste la sesión en este equipo y en los demás donde estaba abierta.',
}

const soloDigitos = (valor: string) => valor.replace(/\D/g, '').slice(0, 6)

/** RF019 · guía §5.3: correo y clave; si la cuenta tiene TOTP, el mismo formulario pide el código y repite. */
export function Ingreso() {
  const { motivo } = useSearch({ from: '/panel/ingreso' })
  const navegar = useNavigate()
  const alcance = useAlcance()
  const [conCodigo, setConCodigo] = useState(false)
  const [correo, setCorreo] = useState('')
  const [clave, setClave] = useState('')
  const [codigo, setCodigo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoIngreso | null>(null)
  const campoCodigo = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (conCodigo) campoCodigo.current?.focus()
  }, [conCodigo])

  if (alcance === 'ALTA_SEGUNDO_FACTOR') return <Redirigir destino={{ to: '/panel/segundo-factor' }} />
  if (alcance === 'COMPLETO') return <Redirigir destino={{ to: '/panel' }} />

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (enviando) return
    setEnviando(true)
    setResultado(null)
    const respuesta = await iniciarSesion(correo.trim(), clave, conCodigo ? codigo : undefined)
    setEnviando(false)
    if (respuesta.tipo === 'ingresado') {
      setClave('')
      void navegar({ to: respuesta.alcance === 'ALTA_SEGUNDO_FACTOR' ? '/panel/segundo-factor' : '/panel', replace: true })
      return
    }
    if (respuesta.tipo === 'pide-codigo') {
      setConCodigo(true)
      return
    }
    if (conCodigo && respuesta.tipo === 'credencial-invalida') setCodigo('')
    setResultado(respuesta)
  }

  function cambiarDeCuenta() {
    setConCodigo(false)
    setClave('')
    setCodigo('')
    setResultado(null)
  }

  const mensaje = resultado ? mensajeIngreso(resultado, conCodigo) : null

  return (
    <div className={`${pagina.pagina} ${estilos.columna}`}>
      <h1 className={pagina.titular}>Ingreso al panel</h1>
      <p className={pagina.entrada}>Para veedores y administradores. Consultar el mapa y reportar no necesitan cuenta.</p>
      {motivo && !resultado && <output className={estilos.aviso}>{MOTIVO[motivo]}</output>}
      <form className={estilos.formulario} onSubmit={(evento) => void enviar(evento)}>
        {conCodigo ? (
          <>
            <p className={estilos.ayuda}>
              La clave de <strong>{correo.trim()}</strong> es correcta. Escribe el código de seis dígitos que muestra tu
              app de autenticación.
            </p>
            <label className={estilos.campo}>
              Código de la app
              <input
                className={`${estilos.entrada} ${estilos.codigo}`}
                value={codigo}
                onChange={(evento) => setCodigo(soloDigitos(evento.target.value))}
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]{6}"
                required
                ref={campoCodigo}
              />
            </label>
          </>
        ) : (
          <>
            <label className={estilos.campo}>
              Correo
              <input
                className={estilos.entrada}
                type="email"
                value={correo}
                onChange={(evento) => setCorreo(evento.target.value)}
                autoComplete="username"
                required
              />
            </label>
            <label className={estilos.campo}>
              Clave
              <input
                className={estilos.entrada}
                type="password"
                value={clave}
                onChange={(evento) => setClave(evento.target.value)}
                autoComplete="current-password"
                required
              />
            </label>
          </>
        )}
        <Button type="submit" className={botones.principal} isDisabled={enviando}>
          {enviando ? 'Ingresando…' : <>Ingresar <span className={botones.flecha} aria-hidden="true">→</span></>}
        </Button>
        {mensaje && <p className={estilos.alerta} role="alert">{mensaje}</p>}
        {conCodigo && (
          <div className={estilos.acciones}>
            <button type="button" className={estilos.enlaceBoton} onClick={cambiarDeCuenta}>Ingresar con otra cuenta</button>
          </div>
        )}
      </form>
      <Link to="/" className={pagina.enlace}>Volver al mapa</Link>
    </div>
  )
}
