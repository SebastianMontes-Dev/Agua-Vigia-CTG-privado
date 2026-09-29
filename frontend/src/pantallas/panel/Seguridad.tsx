import { useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { cambiarClave, cerrarSesion, desactivarSegundoFactor, validarClaveNueva } from '../../api/panel'
import type { ErrorApi } from '../../api/cliente'
import { useCuenta } from '../../app/sesion-panel'
import botones from '../../componentes/Botones.module.css'
import { formatearNumero } from '../../dominio/formato'
import { AltaSegundoFactor } from './AltaSegundoFactor'
import { CampoClave } from './CampoClave'
import estilos from './Formulario.module.css'

export function mensajeDeCambioDeClave(error: ErrorApi): string {
  if (error.estado === null) return 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.'
  if (error.tipo === 'cuenta-bloqueada') {
    const segundos = Number(error.problema?.segundosRestantes)
    return Number.isFinite(segundos)
      ? `Hubo demasiados intentos fallidos. Espera ${formatearNumero(Math.max(1, Math.ceil(segundos / 60)))} min.`
      : 'Hubo demasiados intentos fallidos. Espera unos minutos.'
  }
  if (error.estado === 429) return 'Hay demasiados intentos desde esta conexión. Espera unos minutos.'
  if (error.estado === 400) return 'La clave de hoy no es correcta, o la nueva no cumple la política o es igual a la de hoy.'
  return 'Algo falló de nuestro lado. Inténtalo otra vez en un momento.'
}

function CambioDeClave() {
  const navegar = useNavigate()
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const problemaLocal = nueva ? validarClaveNueva(nueva) : null

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (enviando) return
    const local = validarClaveNueva(nueva)
    if (local) { setMensaje(local); return }
    if (nueva === actual) { setMensaje('La clave nueva tiene que ser distinta de la de hoy.'); return }
    setEnviando(true)
    setMensaje(null)
    const resultado = await cambiarClave(actual, nueva)
    setEnviando(false)
    if (!resultado.ok) { setMensaje(mensajeDeCambioDeClave(resultado.error)); return }
    void navegar({ to: '/panel/ingreso', search: { motivo: 'clave-cambiada' }, replace: true })
  }

  return (
    <section className={estilos.seccion} aria-labelledby="titulo-clave">
      <h2 id="titulo-clave">Cambiar mi clave</h2>
      <p className={estilos.nota}>Al cambiarla se cierran todas tus sesiones, también esta: tendrás que ingresar con la clave nueva. Te avisamos por correo.</p>
      <form className={estilos.formulario} onSubmit={(evento) => void enviar(evento)}>
        <CampoClave etiqueta="Clave de hoy" autocompletar="current-password" valor={actual} alCambiar={setActual} />
        <CampoClave etiqueta="Clave nueva" autocompletar="new-password" valor={nueva} alCambiar={setNueva}
          invalido={problemaLocal !== null} ayuda="De 12 a 128 caracteres, distinta de la de hoy." />
        {problemaLocal && <p className={estilos.ayuda}>{problemaLocal}</p>}
        <Button type="submit" className={botones.principal} isDisabled={enviando}>
          {enviando ? 'Guardando…' : <>Guardar clave nueva <span className={botones.flecha} aria-hidden="true">→</span></>}
        </Button>
        {mensaje && <p role="alert" className={estilos.aviso}>{mensaje}</p>}
      </form>
    </section>
  )
}

type Modo = 'reposo' | 'activar' | 'cambiar' | 'desactivar'

function SegundoFactor() {
  const cuenta = useCuenta()
  const navegar = useNavigate()
  const clientes = useQueryClient()
  const [modo, setModo] = useState<Modo>('reposo')
  const [codigo, setCodigo] = useState('')
  const [trabajando, setTrabajando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const activo = cuenta.data?.segundoFactorActivo === true
  const esAdmin = cuenta.data?.rol === 'ADMIN'

  function listo() {
    setModo('reposo')
    void clientes.invalidateQueries({ queryKey: ['cuenta'] })
  }

  async function desactivar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (trabajando) return
    setTrabajando(true)
    setMensaje(null)
    const resultado = await desactivarSegundoFactor(codigo.trim())
    setTrabajando(false)
    if (!resultado.ok) {
      setMensaje(resultado.error.estado === 401 || resultado.error.estado === 400
        ? 'El código no es correcto.' : 'No pudimos desactivarlo. Inténtalo otra vez en un momento.')
      setCodigo('')
      return
    }
    // La baja cierra todas las sesiones: el token de esta ya no sirve.
    await cerrarSesion()
    void navegar({ to: '/panel/ingreso', search: { motivo: 'segundo-factor-desactivado' }, replace: true })
  }

  return (
    <section className={estilos.seccion} aria-labelledby="titulo-totp">
      <h2 id="titulo-totp">Segundo factor</h2>
      <p className={estilos.nota}>
        {activo ? 'Está activo: al ingresar te pedimos, además de la clave, un código de tu app de autenticación.'
          : 'Está desactivado. Es opcional para veedores y observadores, y obligatorio para administradores.'}
      </p>
      {modo === 'reposo' && (
        <div className={estilos.enlaces}>
          {!activo && <button type="button" className={estilos.enlace} onClick={() => setModo('activar')}>Activar segundo factor</button>}
          {activo && <button type="button" className={estilos.enlace} onClick={() => setModo('cambiar')}>Cambiar de teléfono</button>}
          {activo && !esAdmin && <button type="button" className={estilos.enlace} onClick={() => setModo('desactivar')}>Desactivarlo</button>}
        </div>
      )}
      {modo === 'activar' && <AltaSegundoFactor automatico exigeCodigoActual={false} alActivar={listo} />}
      {modo === 'cambiar' && <AltaSegundoFactor automatico={false} exigeCodigoActual alActivar={listo} />}
      {modo === 'desactivar' && (
        <form className={estilos.formulario} onSubmit={(evento) => void desactivar(evento)}>
          <p className={estilos.nota}>Al desactivarlo se cierran todas tus sesiones y tendrás que volver a ingresar solo con tu clave.</p>
          <label className={estilos.campo}>
            Código de tu app de autenticación
            <input className={estilos.codigo} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6}
              required value={codigo} onChange={(evento) => setCodigo(evento.target.value.replace(/\D/g, ''))} />
          </label>
          <Button type="submit" className={botones.secundario} isDisabled={trabajando}>Desactivar y cerrar mis sesiones</Button>
          {mensaje && <p role="alert" className={estilos.aviso}>{mensaje}</p>}
        </form>
      )}
      {modo !== 'reposo' && <button type="button" className={estilos.enlace} onClick={() => { setModo('reposo'); setMensaje(null) }}>Cancelar</button>}
    </section>
  )
}

function CierreDeSesiones() {
  const navegar = useNavigate()
  const [trabajando, setTrabajando] = useState(false)
  return (
    <section className={estilos.seccion} aria-labelledby="titulo-sesiones">
      <h2 id="titulo-sesiones">Mis sesiones</h2>
      <p className={estilos.nota}>Cierra la sesión en todos los equipos donde hayas ingresado, esta incluida.</p>
      <Button className={botones.secundario} isDisabled={trabajando} onPress={() => {
        setTrabajando(true)
        void cerrarSesion().then(() => navegar({ to: '/panel/ingreso', replace: true }))
      }}>Cerrar todas mis sesiones</Button>
    </section>
  )
}

/** `/panel/seguridad` (guía §5.4): clave, segundo factor y sesiones; siempre disponible para cualquier cuenta con sesión completa. */
export function Seguridad() {
  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Panel del veedor · mi cuenta</p>
        <h1 className={estilos.titular}>Seguridad de tu cuenta</h1>
      </header>
      <div className={estilos.secciones}>
        <CambioDeClave />
        <SegundoFactor />
        <CierreDeSesiones />
      </div>
    </div>
  )
}
