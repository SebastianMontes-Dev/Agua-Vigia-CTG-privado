import { useId, useRef, useState } from 'react'
import { Button, Dialog, DialogTrigger, Heading, Modal, ModalOverlay } from 'react-aria-components'
import { enviarFoto, enviarReporte, TIPOS_FOTO, type ResultadoEnvio, type ResultadoFoto, type TipoReporte } from '../../api/reportes'
import { ubicacionPara } from '../../app/ubicacion'
import { MarcaRecibido } from '../../componentes/MarcaRecibido'
import { nombreLegible, type Sector } from '../../dominio/sectores'
import botones from '../../componentes/Botones.module.css'
import estilos from './Reporte.module.css'

const OPCIONES: { tipo: TipoReporte; texto: string }[] = [
  { tipo: 'SIN_AGUA', texto: 'No tengo agua' },
  { tipo: 'PRESION_BAJA', texto: 'Llega poca agua' },
  { tipo: 'SERVICIO_RESTABLECIDO', texto: 'Ya volvió el agua' },
]

const PARA_COMPARTIR: Record<TipoReporte, (barrio: string) => string> = {
  SIN_AGUA: (barrio) => `En ${barrio} no llega agua a mi casa. Si a la tuya tampoco, confirma mi reporte en AguaVigía:`,
  PRESION_BAJA: (barrio) => `En ${barrio} llega poca agua a mi casa. Si a la tuya también, confirma mi reporte en AguaVigía:`,
  SERVICIO_RESTABLECIDO: (barrio) => `En ${barrio} ya volvió el agua a mi casa. Si a la tuya también, confirma mi reporte en AguaVigía:`,
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
    <div className={estilos.anexo}>
      <input
        ref={entrada}
        id={`foto-${reporteId}`}
        type="file"
        accept={TIPOS_FOTO.join(',')}
        className={estilos.archivo}
        disabled={enviando}
        onChange={(evento) => void elegir(evento.target.files?.[0])}
      />
      <label htmlFor={`foto-${reporteId}`} className={botones.secundario} aria-disabled={enviando}>
        {enviando ? 'Enviando foto…' : 'Añadir una foto'}
      </label>
      <p className={estilos.ayuda}>Opcional. JPEG, PNG o WebP de hasta 10 MB; se le quita la ubicación antes de guardarla.</p>
      {resultado && <p className={estilos.error} role="alert">{mensajeFoto(resultado)}</p>}
    </div>
  )
}

/** RF038: no hay listado público de reportes; el único que un vecino puede confirmar es el que le comparten. */
function Compartir({ reporteId, texto }: { reporteId: string; texto: string }) {
  const [copia, setCopia] = useState<'copiado' | 'manual' | null>(null)
  const idEnlace = useId()
  const enlace = `${window.location.origin}/confirmar/${encodeURIComponent(reporteId)}`

  async function compartir() {
    if (typeof navigator.share === 'function') {
      try {
        await navigator.share({ title: 'Confirma mi reporte', text: texto, url: enlace })
        return
      } catch (error) {
        if ((error as DOMException).name === 'AbortError') return
      }
    }
    try {
      await navigator.clipboard.writeText(`${texto} ${enlace}`)
      setCopia('copiado')
    } catch {
      setCopia('manual')
    }
  }

  return (
    <div className={estilos.anexo}>
      <Button className={botones.secundario} onPress={() => void compartir()}>Pedir a un vecino que lo confirme</Button>
      <p className={estilos.ayuda}>Quien reciba el enlace puede confirmar tu reporte con un toque, sin cuenta.</p>
      {copia === 'copiado' && <output className={estilos.estado}>Enlace copiado. Pégalo en el mensaje para tu vecino.</output>}
      {copia === 'manual' && (
        <div className={estilos.enlaceManual}>
          <label htmlFor={idEnlace} className={estilos.ayuda}>Copia este enlace y envíaselo:</label>
          <input id={idEnlace} className={estilos.enlace} readOnly value={enlace} onFocus={(evento) => evento.target.select()} />
        </div>
      )}
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
  const coordenada = ubicacionPara(sector.id ?? '')

  async function enviar(tipo: TipoReporte) {
    if (enviando) return
    setEnviando(tipo)
    setResultado(null)
    setResultado(await enviarReporte(sector.id ?? '', tipo, coordenada))
    setEnviando(null)
  }

  return (
    <DialogTrigger onOpenChange={(abierto) => { if (!abierto) setResultado(null) }}>
      <Button className={botones.principal}>
        Reportar lo que pasa en mi casa <span className={botones.flecha} aria-hidden="true">→</span>
      </Button>
      <ModalOverlay className={estilos.fondo} isDismissable>
        <Modal className={estilos.cajon}>
          <Dialog className={estilos.dialogo}>
            {({ close }) => resultado?.tipo === 'recibido' ? (
              <div className={estilos.recibido}>
                <MarcaRecibido />
                <Heading slot="title" className={estilos.titulo}>Reporte recibido</Heading>
                <output>Tu reporte cuenta junto con los de tus vecinos. El mapa cambia cuando varios coinciden.</output>
                <Compartir
                  reporteId={resultado.reporte.id ?? ''}
                  texto={PARA_COMPARTIR[(resultado.reporte.tipo as TipoReporte | undefined) ?? 'SIN_AGUA'](nombre)}
                />
                <Foto reporteId={resultado.reporte.id ?? ''} />
                <Button className={botones.secundario} onPress={close}>Volver a {nombre}</Button>
              </div>
            ) : (
              <>
                <Heading slot="title" className={estilos.titulo}>¿Qué pasa con el agua en tu casa?</Heading>
                <p className={estilos.contexto}>
                  Reportas en <strong>{nombre}</strong>{coordenada && ', con la ubicación que compartiste'}. No hace falta cuenta.
                </p>
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
