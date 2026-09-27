import { origenEnPalabras, promesaVencida, type Corte } from '../dominio/cortes'
import { diaRelativo, partirHora } from '../dominio/tiempo'
import estilos from './CorteAbierto.module.css'

function Hora({ rotulo, valor, ahora, vencida = false }: { rotulo: string; valor: string | undefined; ahora: Date; vencida?: boolean }) {
  if (!valor) {
    return (
      <div className={estilos.hora}>
        <dt className={estilos.rotulo}>{rotulo}</dt>
        <dd className={estilos.sinDato}>No informada</dd>
      </div>
    )
  }
  const { cifra, periodo } = partirHora(valor)
  return (
    <div className={estilos.hora}>
      <dt className={estilos.rotulo}>{rotulo}</dt>
      <dd>
        <time dateTime={valor} className={estilos.cifra}>
          <span className="cifra">{cifra}</span> <span className={estilos.periodo}>{periodo}</span>
        </time>
        <span className={estilos.dia}>{diaRelativo(valor, ahora)}{vencida ? ' (hora ya pasada)' : ''}</span>
      </dd>
    </div>
  )
}

/** Regla de inicio a fin prometido con la marca de ahora; crece desde su origen (identidad.md §5). */
function ReglaTiempo({ corte, ahora }: { corte: Corte; ahora: Date }) {
  if (!corte.inicio || !corte.finPrometido) return null
  const inicio = new Date(corte.inicio).getTime()
  const fin = new Date(corte.finPrometido).getTime()
  if (fin <= inicio) return null
  const avance = (ahora.getTime() - inicio) / (fin - inicio)
  const posicion = Math.min(1, Math.max(0, avance))
  const mostrarAhora = avance >= 0
  return (
    <div className={estilos.regla} aria-hidden="true">
      <span className={estilos.eje} />
      <span className={estilos.recorrido} style={{ transform: `scaleX(${posicion})` }} />
      <span className={`${estilos.extremo} ${estilos.inicio}`} />
      <span className={`${estilos.extremo} ${estilos.fin}`} />
      {mostrarAhora && (
        <span className={estilos.ahora} style={{ left: `${posicion * 100}%` }}>
          <span className={estilos.ahoraRotulo}>Ahora</span>
        </span>
      )}
    </div>
  )
}

/**
 * Guía §5.1: cada corte abierto con su propio inicio, fin prometido y origen. No se atribuye el estado del barrio a
 * este corte: el estado puede venir del consenso de vecinos.
 */
export function CorteAbierto({ corte, ahora }: { corte: Corte; ahora: Date }) {
  const origen = origenEnPalabras(corte.origen)
  const vencida = promesaVencida(corte, ahora)
  return (
    <article className={estilos.corte} aria-label="Corte registrado para este barrio">
      <dl className={estilos.horas}>
        <Hora rotulo="Inicio anunciado" valor={corte.inicio} ahora={ahora} />
        <Hora rotulo="Fin prometido" valor={corte.finPrometido} ahora={ahora} vencida={vencida} />
      </dl>
      <ReglaTiempo corte={corte} ahora={ahora} />
      {(origen || corte.causa) && (
        <p className={estilos.origen}>
          {origen && <>{origen}.</>} {corte.causa && <>{corte.causa}.</>}
        </p>
      )}
    </article>
  )
}
