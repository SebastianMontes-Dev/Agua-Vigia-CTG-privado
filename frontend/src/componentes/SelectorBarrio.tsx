import { useMemo, useState } from 'react'
import { ComboBox, Input, Label, ListBox, ListBoxItem, Popover } from 'react-aria-components'
import { buscarSectores, nombreLegible, type Sector } from '../dominio/sectores'
import estilos from './SelectorBarrio.module.css'

interface Props {
  sectores: readonly Sector[]
  valor?: string
  alCambiar: (id?: string) => void
  etiqueta?: string
}

export function SelectorBarrio({ sectores, valor, alCambiar, etiqueta = 'Barrio' }: Props) {
  const [termino, setTermino] = useState('')
  const seleccionado = sectores.find((sector) => sector.id === valor)
  const opciones = useMemo(() => termino ? buscarSectores(sectores, termino, 211) : sectores,
    [sectores, termino])

  return (
    <div className={estilos.conjunto}>
      <ComboBox
        className={estilos.selector}
        selectedKey={valor ?? null}
        inputValue={termino || (seleccionado ? nombreLegible(seleccionado.nombre) : '')}
        onInputChange={setTermino}
        onSelectionChange={(clave) => {
          setTermino('')
          if (clave !== null) alCambiar(String(clave))
        }}
        items={opciones}
        menuTrigger="input"
        allowsEmptyCollection
      >
        <Label className={estilos.etiqueta}>{etiqueta}</Label>
        <Input className={estilos.entrada} placeholder="Todos los barrios" autoComplete="off" spellCheck={false} />
        <Popover className={estilos.menu} offset={4}>
          <ListBox<Sector> className={estilos.lista} renderEmptyState={() => <p className={estilos.vacio}>No encontramos barrios con ese nombre</p>}>
            {(sector) => <ListBoxItem id={sector.id} textValue={nombreLegible(sector.nombre)} className={estilos.opcion}>
              {nombreLegible(sector.nombre)}
            </ListBoxItem>}
          </ListBox>
        </Popover>
      </ComboBox>
      {valor && <button type="button" className={estilos.limpiar} onClick={() => { setTermino(''); alCambiar(undefined) }}>Quitar barrio</button>}
    </div>
  )
}
