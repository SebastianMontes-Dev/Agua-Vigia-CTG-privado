import { useState } from 'react'
import { Radio, RadioGroup, Label } from 'react-aria-components'
import { aplicarTema, leerPreferenciaTema, type PreferenciaTema } from './tema'
import estilos from './Marco.module.css'

const OPCIONES: { valor: PreferenciaTema; texto: string }[] = [
  { valor: 'sistema', texto: 'Sistema' },
  { valor: 'claro', texto: 'Claro' },
  { valor: 'oscuro', texto: 'Oscuro' },
]

export function SelectorTema() {
  const [tema, setTema] = useState<PreferenciaTema>(leerPreferenciaTema)
  return (
    <RadioGroup
      className={estilos.tema}
      orientation="horizontal"
      value={tema}
      onChange={(valor) => {
        const preferencia = valor as PreferenciaTema
        setTema(preferencia)
        aplicarTema(preferencia)
      }}
    >
      <Label className={estilos.oculto}>Tema</Label>
      {OPCIONES.map((opcion) => (
        <Radio key={opcion.valor} value={opcion.valor} className={estilos.opcionTema}>{opcion.texto}</Radio>
      ))}
    </RadioGroup>
  )
}
