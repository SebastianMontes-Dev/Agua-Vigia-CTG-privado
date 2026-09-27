import type { LecturaCanal } from '../api/canal-en-vivo'
import { momento } from '../dominio/tiempo'
import estilos from './EstadoListado.module.css'

interface Props {
  lectura: LecturaCanal
  ahora: Date
  reintentar: () => void
}

/**
 * Guía §6.1: la hora del listado, la conectividad y el «En vivo» son relojes distintos. «En vivo» solo mientras el
 * canal esté conectado; una reconexión normal no se presenta como error.
 */
export function EstadoListado({ lectura, ahora, reintentar }: Props) {
  const { listado, estado, error } = lectura

  if (!listado) {
    if (error) {
      return (
        <div className={estilos.aviso} role="alert">
          <p>No pudimos consultar el estado. Revisa tu conexión e inténtalo otra vez.</p>
          <button type="button" className={estilos.reintentar} onClick={reintentar}>Reintentar</button>
        </div>
      )
    }
    return <output className={estilos.linea}>Consultando el estado del agua…</output>
  }

  const generado = <time dateTime={listado.generadoEn}>{momento(listado.generadoEn, ahora)}</time>

  if (estado === 'sin-red') {
    return (
      <output className={estilos.aviso}>
        Sin conexión. Mostramos el último listado guardado, generado {generado}.
      </output>
    )
  }

  if (error) {
    return (
      <div className={estilos.aviso}>
        <output>No pudimos actualizar. Mostramos el último listado disponible, generado {generado}.</output>
        <button type="button" className={estilos.reintentar} onClick={reintentar}>Reintentar</button>
      </div>
    )
  }

  return (
    <p className={estilos.linea}>
      {estado === 'en-vivo' && <span className={estilos.enVivo}><span className={estilos.punto} aria-hidden="true" />En vivo</span>}
      <span>Listado generado {generado}</span>
    </p>
  )
}
