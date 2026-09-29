import { useId, useState } from 'react'
import estilos from './Formulario.module.css'

interface Props {
  etiqueta: string
  valor: string
  alCambiar: (valor: string) => void
  /** `current-password` al ingresar; `new-password` al fijar una: así el gestor de contraseñas ofrece lo correcto. */
  autocompletar: 'current-password' | 'new-password'
  ayuda?: string
  invalido?: boolean
}

/** Permite pegar y usar gestor de contraseñas; «Mostrar» revela lo escrito. No hay medidor de fuerza inventado (guía §5.3). */
export function CampoClave({ etiqueta, valor, alCambiar, autocompletar, ayuda, invalido }: Props) {
  const [visible, setVisible] = useState(false)
  const idAyuda = useId()
  return (
    <div className={estilos.campo}>
      <label className={estilos.campo}>
        {etiqueta}
        <input
          type={visible ? 'text' : 'password'}
          autoComplete={autocompletar}
          required
          value={valor}
          aria-invalid={invalido || undefined}
          aria-describedby={ayuda ? idAyuda : undefined}
          onChange={(evento) => alCambiar(evento.target.value)}
        />
      </label>
      <button type="button" className={estilos.enlace} aria-pressed={visible} onClick={() => setVisible(!visible)}>
        {visible ? 'Ocultar lo que escribes' : 'Mostrar lo que escribes'}
      </button>
      {ayuda && <span id={idAyuda} className={estilos.ayuda}>{ayuda}</span>}
    </div>
  )
}
