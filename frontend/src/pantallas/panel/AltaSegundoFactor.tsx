import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import { confirmarSegundoFactor, pedirAltaSegundoFactor } from '../../api/panel'
import type { ErrorApi } from '../../api/cliente'
import botones from '../../componentes/Botones.module.css'
import { CodigoQr } from './CodigoQr'
import estilos from './Formulario.module.css'

interface Props {
  /** Quien ya tiene un segundo factor (cambio de teléfono) debe dar el código vigente para rehacer el alta. */
  exigeCodigoActual: boolean
  /** El primer ingreso de un ADMIN genera el secreto al abrir la pantalla; el cambio espera al código actual. */
  automatico: boolean
  alActivar: () => void
}

interface Alta { uri: string; secreto: string }

export function mensajeDeAlta(error: ErrorApi, etapa: 'generar' | 'confirmar'): string {
  if (error.estado === null) return 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.'
  if (etapa === 'generar') {
    if (error.estado === 409) return 'Escribe el código actual de tu app de autenticación para cambiarla.'
    if (error.estado === 401) return 'El código actual no es correcto.'
  } else if (error.estado === 400 || error.estado === 401) {
    return 'El código no es correcto. Espera al siguiente código de tu app y vuelve a escribirlo.'
  }
  if (error.estado === 429) return 'Hay demasiados intentos desde esta conexión. Espera unos minutos.'
  return 'Algo falló de nuestro lado. Inténtalo otra vez en un momento.'
}

/** Alta del segundo factor en dos pasos: el secreto se genera sin activarse y solo se activa con un código válido. */
export function AltaSegundoFactor({ exigeCodigoActual, automatico, alActivar }: Props) {
  const [alta, setAlta] = useState<Alta | null>(null)
  const [codigoActual, setCodigoActual] = useState('')
  const [codigo, setCodigo] = useState('')
  const [trabajando, setTrabajando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const iniciado = useRef(false)

  async function generar(codigoVigente?: string) {
    setTrabajando(true)
    setMensaje(null)
    const resultado = await pedirAltaSegundoFactor(codigoVigente)
    setTrabajando(false)
    if (!resultado.ok) { setMensaje(mensajeDeAlta(resultado.error, 'generar')); return }
    const { uri, secreto } = resultado.datos
    if (uri && secreto) setAlta({ uri, secreto })
    else setMensaje('Algo falló de nuestro lado. Inténtalo otra vez en un momento.')
  }

  useEffect(() => {
    if (!automatico || iniciado.current) return
    iniciado.current = true
    void generar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [automatico])

  async function activar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (trabajando) return
    setTrabajando(true)
    setMensaje(null)
    const resultado = await confirmarSegundoFactor(codigo.trim())
    setTrabajando(false)
    if (!resultado.ok) { setMensaje(mensajeDeAlta(resultado.error, 'confirmar')); setCodigo(''); return }
    setAlta(null)
    alActivar()
  }

  if (!alta) {
    return (
      <div className={estilos.formulario}>
        {exigeCodigoActual ? (
          <form className={estilos.formulario} onSubmit={(evento) => { evento.preventDefault(); void generar(codigoActual.trim()) }}>
            <label className={estilos.campo}>
              Código actual de tu app de autenticación
              <input className={estilos.codigo} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6}
                required value={codigoActual} onChange={(evento) => setCodigoActual(evento.target.value.replace(/\D/g, ''))} />
              <span className={estilos.ayuda}>Sin él, cualquiera con tu sesión abierta podría cambiarte el segundo factor por el suyo.</span>
            </label>
            <Button type="submit" className={botones.secundario} isDisabled={trabajando}>Generar un código QR nuevo</Button>
          </form>
        ) : (
          <output className={estilos.nota}>{trabajando ? 'Preparando tu código QR…' : 'Preparando tu código QR.'}</output>
        )}
        {mensaje && <p role="alert" className={estilos.aviso}>{mensaje}</p>}
        {!exigeCodigoActual && !trabajando && mensaje && (
          <button type="button" className={estilos.enlace} onClick={() => void generar()}>Volver a intentar</button>
        )}
      </div>
    )
  }

  return (
    <form className={estilos.formulario} onSubmit={(evento) => void activar(evento)}>
      <p className={estilos.nota}>Escanea el código con tu app de autenticación. Solo se muestra ahora: si sales de esta pantalla, tendrás que generar otro.</p>
      <CodigoQr contenido={alta.uri} titulo="Código QR para tu app de autenticación" />
      <div>
        <p className={estilos.nota}>¿La cámara no lo lee? Escribe esta clave en tu app:</p>
        <p className={estilos.secreto}>{alta.secreto}</p>
      </div>
      <label className={estilos.campo}>
        Código de seis dígitos
        <input className={estilos.codigo} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6}
          required value={codigo} onChange={(evento) => setCodigo(evento.target.value.replace(/\D/g, ''))} />
      </label>
      <Button type="submit" className={botones.principal} isDisabled={trabajando}>
        {trabajando ? 'Comprobando…' : <>Activar segundo factor <span className={botones.flecha} aria-hidden="true">→</span></>}
      </Button>
      {mensaje && <p role="alert" className={estilos.aviso}>{mensaje}</p>}
    </form>
  )
}
