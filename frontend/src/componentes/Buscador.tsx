import { useMemo, useState } from 'react'
import { ComboBox, Input, Label, ListBox, ListBoxItem, Popover } from 'react-aria-components'
import { GlifoEstado } from './GlifoEstado'
import { presentarEstado } from '../dominio/estados'
import { buscarSectores, nombreLegible, type Sector } from '../dominio/sectores'
import estilos from './Buscador.module.css'

interface Props {
  sectores: readonly Sector[]
  cargando: boolean
  alElegir: (id: string) => void
}

/** Guía §3: combobox etiquetado; flechas para las sugerencias, Enter para elegir y Escape para cerrar. */
export function Buscador({ sectores, cargando, alElegir }: Props) {
  const [termino, setTermino] = useState('')
  const sugerencias = useMemo(() => buscarSectores(sectores, termino), [sectores, termino])

  return (
    <ComboBox
      className={estilos.buscador}
      inputValue={termino}
      onInputChange={setTermino}
      items={sugerencias}
      menuTrigger="input"
      allowsEmptyCollection
      onSelectionChange={(clave) => {
        if (clave === null) return
        setTermino('')
        alElegir(String(clave))
      }}
    >
      <Label className={estilos.etiqueta}>Busca tu barrio</Label>
      <div className={estilos.campo}>
        <Input className={estilos.entrada} placeholder="Escribe el nombre de tu barrio" autoComplete="off" spellCheck={false} />
        <svg className={estilos.lupa} viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="10.5" cy="10.5" r="6.5" />
          <path d="M15.5 15.5 21 21" />
        </svg>
      </div>
      <Popover className={estilos.sugerencias} offset={4}>
        <ListBox<Sector>
          className={estilos.lista}
          renderEmptyState={() => (
            <p className={estilos.vacio}>
              {cargando ? 'Consultando el estado del agua…' : 'No encontramos barrios con ese nombre'}
            </p>
          )}
        >
          {(sector) => (
            <ListBoxItem id={sector.id} textValue={nombreLegible(sector.nombre)} className={estilos.opcion}>
              <span className={estilos.nombre}>{nombreLegible(sector.nombre)}</span>
              <span className={estilos.estado}>
                <GlifoEstado estado={sector.estado} tamano={16} />
                {presentarEstado(sector.estado).texto}
              </span>
            </ListBoxItem>
          )}
        </ListBox>
      </Popover>
    </ComboBox>
  )
}
