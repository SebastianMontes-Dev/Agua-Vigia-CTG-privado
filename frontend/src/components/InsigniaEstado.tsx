/**
 * InsigniaEstado — pastilla visual que muestra el estado del servicio.
 *
 * DESIGN.md §2: "El color nunca va solo. Cada estado se acompaña
 * de forma o texto." Este componente siempre muestra el punto + etiqueta.
 */
import type { FC } from 'react'
import type { EstadoServicio } from '../types/tipos-dominio'
import { COLOR_POR_ESTADO, COLOR_SIN_DATOS } from '../types/tipos-dominio'
import { useTemaActivo } from '../hooks/useTemaActivo'

interface Props {
  estado: EstadoServicio | null
  /**
   * Normalmente no hace falta: el tema activo se detecta solo (useTemaActivo). Ningún llamador
   * lo pasaba — por eso, en tema oscuro, esta insignia venía usando siempre la paleta de claro
   * (ADR-042 fijó las dos paletas por contraste, y esto reabría el problema que esa decisión
   * cerró). Queda como escape hatch explícito para el caso raro de un fondo fijo que no sigue
   * el tema global de la página.
   */
  modoOscuro?: boolean
  tamaño?: 'sm' | 'md'
}

export const InsigniaEstado: FC<Props> = ({ estado, modoOscuro, tamaño = 'md' }) => {
  const temaActivo = useTemaActivo()
  const oscuro = modoOscuro ?? temaActivo === 'oscuro'
  const colores = estado ? COLOR_POR_ESTADO[estado] : COLOR_SIN_DATOS
  const color = oscuro ? colores.oscuro : colores.claro
  const fontSize = tamaño === 'sm' ? '0.75rem' : '0.875rem'
  const dotSize = tamaño === 'sm' ? '8px' : '10px'

  return (
    <span
      role="status"
      aria-label={`Estado: ${colores.etiqueta}`}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '0.35rem',
        fontSize,
        fontFamily: 'var(--font-util)',
        color: 'var(--color-tinta)',
        fontWeight: '500',
      }}
    >
      {/* Punto de color — nunca va solo, siempre acompañado por el texto */}
      <span
        aria-hidden="true"
        style={{
          width: dotSize,
          height: dotSize,
          borderRadius: '50%',
          backgroundColor: color,
          flexShrink: 0,
          display: 'inline-block',
        }}
      />
      {colores.etiqueta}
    </span>
  )
}
