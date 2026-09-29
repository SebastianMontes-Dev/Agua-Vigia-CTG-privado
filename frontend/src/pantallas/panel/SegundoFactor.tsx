import { Link, useNavigate } from '@tanstack/react-router'
import { Redirigir } from '../../app/Redirigir'
import { useState, type FormEvent } from 'react'
import { Button } from 'react-aria-components'
import {
  cerrarSesion,
  confirmarSegundoFactor,
  iniciarAltaSegundoFactor,
  type ResultadoAlta,
  type ResultadoConfirmacionFactor,
} from '../../api/panel'
import { useAlcance, useSesionPerdida } from '../../app/panel'
import { CodigoQr } from '../../componentes/CodigoQr'
import { mensajeSegundoFactor } from '../../dominio/mensajes-panel'
import botones from '../../componentes/Botones.module.css'
import pagina from '../publico/Pagina.module.css'
import estilos from './Formulario.module.css'

const soloDigitos = (valor: string) => valor.replace(/\D/g, '').slice(0, 6)

/** Grupos de cuatro para copiarlo a mano sin perderse; la app lo acepta con o sin espacios. */
export function agruparSecreto(secreto: string): string {
  return secreto.replace(/\s/g, '').match(/.{1,4}/g)?.join(' ') ?? secreto
}

/**
 * RNF025 · guía §5.3: el alta se pide al tocar, no al montar, porque cada alta reemplaza el secreto pendiente; el
 * secreto se muestra una sola vez y el segundo factor solo queda activo tras confirmar un código.
 */
export function SegundoFactor() {
  const alcance = useAlcance()
  const perdida = useSesionPerdida(alcance)
  const navegar = useNavigate()
  const [alta, setAlta] = useState<{ uri: string; secreto: string } | null>(null)
  const [codigo, setCodigo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoAlta | ResultadoConfirmacionFactor | null>(null)
  const [saliendo, setSaliendo] = useState(false)

  if (!alcance) return <Redirigir destino={{ to: '/panel/ingreso', search: saliendo ? { motivo: 'cerrada' } : perdida ? { motivo: 'vencida' } : {} }} />

  const restringida = alcance === 'ALTA_SEGUNDO_FACTOR'

  async function generar() {
    if (enviando) return
    setEnviando(true)
    setResultado(null)
    const respuesta = await iniciarAltaSegundoFactor()
    setEnviando(false)
    if (respuesta.tipo === 'alta') {
      setAlta({ uri: respuesta.uri, secreto: respuesta.secreto })
      setCodigo('')
      return
    }
    setResultado(respuesta)
  }

  async function activar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (enviando) return
    setEnviando(true)
    setResultado(null)
    const respuesta = await confirmarSegundoFactor(codigo)
    setEnviando(false)
    if (respuesta.tipo === 'activado') {
      setAlta(null)
      void navegar({ to: '/panel', replace: true })
      return
    }
    if (respuesta.tipo === 'codigo-incorrecto') setCodigo('')
    if (respuesta.tipo === 'sin-alta') setAlta(null)
    setResultado(respuesta)
  }

  // Al borrar el token, la guarda de arriba lleva al ingreso con el motivo correcto.
  function salir() {
    setSaliendo(true)
    void cerrarSesion()
  }

  const mensaje = resultado ? mensajeSegundoFactor(resultado) : null

  return (
    <div className={`${pagina.pagina} ${estilos.columna}`}>
      <h1 className={pagina.titular}>Segundo factor</h1>
      <p className={pagina.entrada}>
        {restringida
          ? 'Tu cuenta de administrador necesita un segundo factor antes de entrar al panel.'
          : 'Protege tu cuenta con un código de tu app de autenticación además de la clave.'}
      </p>
      <ol className={estilos.pasos}>
        <li className={estilos.paso}>
          <h2>Genera el código QR</h2>
          <p className={estilos.ayuda}>Necesitas una app de autenticación en el teléfono, como las de Google o Microsoft.</p>
          {!alta && (
            <Button className={botones.secundario} isDisabled={enviando} onPress={() => void generar()}>
              {enviando ? 'Generando…' : 'Generar código QR'}
            </Button>
          )}
        </li>
        <li className={estilos.paso}>
          <h2>Escanéalo con la app</h2>
          {alta ? (
            <>
              <CodigoQr texto={alta.uri} />
              <p className={estilos.ayuda}>Si la cámara no lo lee, escribe esta clave en la app:</p>
              <p className={estilos.secreto}>{agruparSecreto(alta.secreto)}</p>
              <p className={estilos.ayuda}>
                La clave se muestra una sola vez. Si recargas la página antes de terminar, tendrás que generar otro código.
              </p>
            </>
          ) : (
            <p className={estilos.ayuda}>El código aparece aquí cuando lo generes.</p>
          )}
        </li>
        <li className={estilos.paso}>
          <h2>Escribe el código que muestra la app</h2>
          <form className={estilos.acciones} onSubmit={(evento) => void activar(evento)}>
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
                disabled={!alta}
              />
            </label>
            <Button type="submit" className={botones.principal} isDisabled={!alta || enviando}>
              {enviando && alta ? 'Activando…' : <>Activar segundo factor <span className={botones.flecha} aria-hidden="true">→</span></>}
            </Button>
          </form>
        </li>
      </ol>
      {mensaje && <p className={estilos.alerta} role="alert">{mensaje}</p>}
      {restringida
        ? <button type="button" className={estilos.enlaceBoton} onClick={salir}>Salir sin configurarlo</button>
        : <Link to="/panel" className={pagina.enlace}>Volver al panel</Link>}
    </div>
  )
}
