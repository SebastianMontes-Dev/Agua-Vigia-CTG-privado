import { useRef, useState } from 'react'
import { Button, Dialog, DialogTrigger, Heading, Modal, ModalOverlay } from 'react-aria-components'
import { enviarFoto, enviarReporte, TIPOS_FOTO, type ResultadoEnvio, type ResultadoFoto, type TipoReporte } from '../../api/reportes'
import { nombreLegible, type Sector } from '../../dominio/sectores'
import estilos from './Reporte.module.css'

const OPCIONES: { tipo: TipoReporte; texto: string }[] = [
  { tipo: 'SIN_AGUA', texto: 'No tengo agua' },
  { tipo: 'PRESION_BAJA', texto: 'Llega poca agua' },
  { tipo: 'SERVICIO_RESTABLECIDO', texto: 'Ya volvió el agua' },
]

function MarcaRecibido() {
  return (
    <svg className={estilos.marca} viewBox="0 0 48 48" aria-hidden="true">
      <circle cx="24" cy="24" r="21" />
      <path d="M14.5 24.5l6.5 6.5 13-14" />
    </svg>
  )
}

function mensajeEnvio(resultado: Exclude<ResultadoEnvio, { tipo: 'recibido' }>): string {
  switch (resultado.tipo) {
    case 'sin-red': return 'No pudimos enviar tu reporte. Cuando recuperes la conexión, inténtalo otra vez.'
    case 'incierto': return 'No pudimos confirmar si se recibió tu reporte. Revisa tu conexión antes de enviarlo otra vez.'
    case 'cupo-agotado': return 'Ya enviaste tres reportes de este barrio en la última media hora. Los que enviaste siguen contando.'
    case 'esperar': return resultado.segundos
      ? `Espera antes de volver a intentarlo. Podrás hacerlo en unos ${resultado.segundos} segundos.`
      : 'Espera antes de volver a intentarlo.'
    case 'rechazado': return resultado.mensaje
    case 'fallo': return 'No pudimos registrar tu reporte. Inténtalo otra vez en un momento.'
  }
}

function mensajeFoto(resultado: ResultadoFoto): string {
  switch (resultado.tipo) {
    case 'recibida': return 'Foto añadida a tu reporte.'
    case 'muy-grande': return 'La foto pesa más de 10 MB. Elige una más liviana.'
    case 'no-valida': return resultado.mensaje
    case 'fallo': return 'No pudimos enviar la foto. Tu reporte ya quedó registrado; puedes intentarlo otra vez.'
  }
}

function Foto({ reporteId }: { reporteId: string }) {
  const entrada = useRef<HTMLInputElement>(null)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoFoto | null>(null)

  async function elegir(archivo: File | undefined) {
    if (!archivo) return
    setEnviando(true)
    setResultado(await enviarFoto(reporteId, archivo))
    setEnviando(false)
    if (entrada.current) entrada.current.value = ''
  }

  if (resultado?.tipo === 'recibida') return <output className={estilos.estado}>{mensajeFoto(resultado)}</output>

  return (
    <div className={estilos.foto}>
      <input
        ref={entrada}
        id={`foto-${reporteId}`}
        type="file"
        accept={TIPOS_FOTO.join(',')}
        className={estilos.archivo}
        disabled={enviando}
        onChange={(evento) => void elegir(evento.target.files?.[0])}
      />
      <label htmlFor={`foto-${reporteId}`} className={estilos.secundario} aria-disabled={enviando}>
        {enviando ? 'Enviando foto…' : 'Añadir una foto'}
      </label>
      <p className={estilos.ayuda}>Opcional. JPEG, PNG o WebP de hasta 10 MB; se le quita la ubicación antes de guardarla.</p>
      {resultado && <p className={estilos.error} role="alert">{mensajeFoto(resultado)}</p>}
    </div>
  )
}

/**
 * RF008: desde la ficha, abrir y elegir es enviar; dos toques. El resultado se dice tal como llegó y el mapa no se
 * toca: el estado cambia solo cuando coinciden los vecinos (docs/api/reportes.md).
 */
export function Reporte({ sector }: { sector: Sector }) {
  const [enviando, setEnviando] = useState<TipoReporte | null>(null)
  const [resultado, setResultado] = useState<ResultadoEnvio | null>(null)
  const nombre = nombreLegible(sector.nombre)

  async function enviar(tipo: TipoReporte) {
    if (enviando) return
    setEnviando(tipo)
    setResultado(null)
    setResultado(await enviarReporte(sector.id ?? '', tipo))
    setEnviando(null)
  }

  return (
    <DialogTrigger onOpenChange={(abierto) => { if (!abierto) setResultado(null) }}>
      <Button className={estilos.principal}>
        Reportar lo que pasa en mi casa <span className={estilos.flecha} aria-hidden="true">→</span>
      </Button>
      <ModalOverlay className={estilos.fondo} isDismissable>
        <Modal className={estilos.cajon}>
          <Dialog className={estilos.dialogo}>
            {({ close }) => resultado?.tipo === 'recibido' ? (
              <div className={estilos.recibido}>
                <MarcaRecibido />
                <Heading slot="title" className={estilos.titulo}>Reporte recibido</Heading>
                <output>Tu reporte cuenta junto con los de tus vecinos. El mapa cambia cuando varios coinciden.</output>
                <Foto reporteId={resultado.reporte.id ?? ''} />
                <Button className={estilos.secundario} onPress={close}>Volver a {nombre}</Button>
              </div>
            ) : (
              <>
                <Heading slot="title" className={estilos.titulo}>¿Qué pasa con el agua en tu casa?</Heading>
                <p className={estilos.contexto}>Reportas en <strong>{nombre}</strong>. No hace falta cuenta.</p>
                <div className={estilos.opciones} aria-busy={enviando !== null}>
                  {OPCIONES.map((opcion) => (
                    <Button
                      key={opcion.tipo}
                      className={estilos.opcion}
                      isDisabled={enviando !== null}
                      onPress={() => void enviar(opcion.tipo)}
                    >
                      {enviando === opcion.tipo ? 'Enviando…' : opcion.texto}
                    </Button>
                  ))}
                </div>
                {resultado && <p className={estilos.error} role="alert">{mensajeEnvio(resultado)}</p>}
                <Button className={estilos.cancelar} onPress={close}>Cancelar</Button>
              </>
            )}
          </Dialog>
        </Modal>
      </ModalOverlay>
    </DialogTrigger>
  )
}
