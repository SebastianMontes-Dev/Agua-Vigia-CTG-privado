import type { ReactNode } from 'react'
import { Button, Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components'
import estilos from './Consola.module.css'

interface Props {
  abierto: boolean
  titulo: string
  /** Debe nombrar lo que se afecta: el barrio, la cuenta, la propuesta. */
  descripcion: ReactNode
  etiquetaConfirmar: string
  ocupado: boolean
  error: string | null
  alConfirmar: () => void
  alCancelar: () => void
}

/** `alertdialog`: el foco entra al diálogo, Escape cancela y confirmar exige un clic o Enter sobre el botón. */
export function ConfirmarAccion({ abierto, titulo, descripcion, etiquetaConfirmar, ocupado, error, alConfirmar, alCancelar }: Props) {
  return (
    <ModalOverlay className={estilos.fondo} isOpen={abierto} onOpenChange={(estado) => { if (!estado && !ocupado) alCancelar() }} isDismissable={!ocupado}>
      <Modal className={estilos.caja}>
        <Dialog role="alertdialog" className={estilos.dialogo}>
          <Heading slot="title">{titulo}</Heading>
          <div>{descripcion}</div>
          {error && <p role="alert" className={estilos.aviso}>{error}</p>}
          <div className={estilos.botones}>
            <Button className={estilos.boton} isDisabled={ocupado} onPress={alCancelar}>Cancelar</Button>
            <Button className={estilos.boton} data-tipo="afirmativo" isDisabled={ocupado} onPress={alConfirmar}>
              {ocupado ? 'Enviando…' : etiquetaConfirmar}
            </Button>
          </div>
        </Dialog>
      </Modal>
    </ModalOverlay>
  )
}
