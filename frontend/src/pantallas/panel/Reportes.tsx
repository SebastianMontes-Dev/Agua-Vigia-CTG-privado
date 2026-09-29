import { useQueryClient } from '@tanstack/react-query'
import { useState, type KeyboardEvent } from 'react'
import { Button } from 'react-aria-components'
import type { ErrorApi } from '../../api/cliente'
import { decidirReporte, type Decision, type ReportePendiente } from '../../api/moderacion'
import { useAhora } from '../../app/ahora'
import { useBarrios } from '../../app/datos'
import { useReportesPendientes } from '../../app/datos-panel'
import { tienePermiso, useCuenta } from '../../app/sesion-panel'
import { formatearNumero } from '../../dominio/formato'
import { nombreLegible } from '../../dominio/sectores'
import { haceCuanto, momento } from '../../dominio/tiempo'
import { ConfirmarAccion } from './ConfirmarAccion'
import estilos from './Consola.module.css'

const TIPO: Record<string, string> = {
  SIN_AGUA: 'Sin agua',
  PRESION_BAJA: 'Presión baja',
  SERVICIO_RESTABLECIDO: 'Servicio restablecido',
}

export function mensajeDeModeracion(error: ErrorApi): string {
  if (error.estado === null) return 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.'
  if (error.estado === 404) return 'Ese reporte ya no está en la cola. Actualizamos la lista.'
  if (error.estado === 403) return 'Tu cuenta no puede moderar reportes.'
  return 'Algo falló de nuestro lado. Inténtalo otra vez en un momento.'
}

function descripcionDe(reporte: ReportePendiente, decision: Decision, barrio: string) {
  const tipo = (TIPO[reporte.tipo ?? ''] ?? 'Un reporte').toLowerCase()
  return decision === 'aprobar'
    ? <p>Vas a aprobar el reporte «{tipo}» de <strong>{barrio}</strong>. Un reporte aprobado sigue contando para el consenso.</p>
    : <p>Vas a descartar el reporte «{tipo}» de <strong>{barrio}</strong>. Dejará de contar para el consenso y las confirmaciones, pero seguirá gastando el cupo de su dispositivo.</p>
}

/** `/panel` (guía §5.4): la cola de moderación, más antiguos primero. Aprobar o descartar siempre pide confirmar. */
export function Reportes() {
  const cuenta = useCuenta()
  const puedeModerar = tienePermiso(cuenta.data, 'MODERAR_REPORTES')
  const cola = useReportesPendientes()
  const barrios = useBarrios()
  const ahora = useAhora()
  const clientes = useQueryClient()
  const [actual, setActual] = useState(0)
  const [pendiente, setPendiente] = useState<{ reporte: ReportePendiente; decision: Decision } | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hecho, setHecho] = useState<string | null>(null)

  const reportes = cola.data?.pages.flatMap((pagina) => pagina.reportes) ?? []
  const total = cola.data?.pages[0]?.paginacion.total ?? null
  const nombres = new Map((barrios.data ?? []).map((barrio) => [barrio.id ?? '', barrio.nombre]))
  const barrioDe = (reporte: ReportePendiente) => nombreLegible(nombres.get(reporte.sectorId ?? '')) || (reporte.sectorId ?? 'Sin barrio')
  const indice = Math.min(actual, Math.max(0, reportes.length - 1))

  function pedir(reporte: ReportePendiente | undefined, decision: Decision) {
    if (!reporte || !puedeModerar) return
    setError(null)
    setPendiente({ reporte, decision })
  }

  async function confirmar() {
    if (!pendiente || ocupado) return
    setOcupado(true)
    setError(null)
    const resultado = await decidirReporte(pendiente.reporte.id ?? '', pendiente.decision)
    setOcupado(false)
    if (resultado.ok) {
      setHecho(`Reporte ${pendiente.decision === 'aprobar' ? 'aprobado' : 'descartado'} en ${barrioDe(pendiente.reporte)}.`)
      setPendiente(null)
      await cola.refetch()
      return
    }
    setError(mensajeDeModeracion(resultado.error))
    if (resultado.error.estado === 404) { setPendiente(null); void cola.refetch() }
    if (resultado.error.estado === 403) void clientes.invalidateQueries({ queryKey: ['cuenta'] })
  }

  // Atajos solo con la propia cola enfocada: dentro de un botón o un campo no hacen nada (guía §4.2).
  function alTeclear(evento: KeyboardEvent<HTMLDivElement>) {
    if (evento.target !== evento.currentTarget || evento.ctrlKey || evento.metaKey || evento.altKey) return
    const tecla = evento.key.toLowerCase()
    if (tecla === 'j') setActual(Math.min(reportes.length - 1, indice + 1))
    else if (tecla === 'k') setActual(Math.max(0, indice - 1))
    else if (tecla === 'a') pedir(reportes[indice], 'aprobar')
    else if (tecla === 'd') pedir(reportes[indice], 'descartar')
    else return
    evento.preventDefault()
  }

  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.rotulo}>Panel · moderación</p>
        <h1 className={estilos.titular}>
          {cola.isSuccess && total !== null
            ? total === 0 ? 'No hay reportes pendientes' : `${formatearNumero(total)} ${total === 1 ? 'reporte espera' : 'reportes esperan'} tu revisión`
            : 'Reportes por revisar'}
        </h1>
        <p className={estilos.entrada}>
          Los más antiguos primero. Un reporte cuenta para el consenso desde que llega: revisarlo no es una puerta previa, es una corrección posterior.
          {!puedeModerar && cuenta.isSuccess && ' Tu cuenta puede ver la cola, pero no decidir.'}
        </p>
      </header>

      {hecho && <output className={estilos.aviso}>{hecho}</output>}

      {cola.isPending ? (
        <output className={estilos.esqueleto} aria-label="Consultando la cola de reportes…"><span /><span /><span /></output>
      ) : cola.isError ? (
        <div>
          <p role="alert" className={estilos.aviso}>No pudimos consultar la cola. Revisa tu conexión e inténtalo otra vez.</p>
          <button type="button" className={estilos.enlace} onClick={() => void cola.refetch()}>Volver a intentar</button>
        </div>
      ) : reportes.length === 0 ? (
        <p className={estilos.vacio}>No hay reportes pendientes. Cuando un vecino reporte, aparecerá aquí.</p>
      ) : (
        <>
          {/* La cola es una región enfocable con atajos de teclado (guía §4.2); cada fila conserva sus botones como camino accesible. */}
          {/* eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex, jsx-a11y/no-noninteractive-element-interactions, jsx-a11y/prefer-tag-over-role */}
          <div className={estilos.cola} role="group" aria-label="Cola de reportes" tabIndex={0} onKeyDown={alTeclear}>
            <ul className={estilos.lista}>
              {reportes.map((reporte, posicion) => (
                <li key={reporte.id} className={estilos.fila} data-actual={posicion === indice}>
                  <div className={estilos.datos}>
                    <div className={estilos.principal}>
                      <strong>{barrioDe(reporte)}</strong>
                      <span>{TIPO[reporte.tipo ?? ''] ?? reporte.tipo}</span>
                    </div>
                    <span className={estilos.secundario}>
                      {reporte.timestamp ? `${haceCuanto(reporte.timestamp, ahora)} · ${momento(reporte.timestamp, ahora)}` : 'Sin hora registrada'}
                      {reporte.coordenada ? ' · con ubicación' : ''}
                    </span>
                  </div>
                  {puedeModerar && (
                    <div className={estilos.acciones}>
                      <Button className={estilos.boton} aria-label={`Aprobar el reporte de ${barrioDe(reporte)}`}
                        onPress={() => { setActual(posicion); pedir(reporte, 'aprobar') }}>Aprobar</Button>
                      <Button className={estilos.boton} aria-label={`Descartar el reporte de ${barrioDe(reporte)}`}
                        onPress={() => { setActual(posicion); pedir(reporte, 'descartar') }}>Descartar</Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </div>
          {puedeModerar && <p className={estilos.atajos}>Con la cola enfocada: j y k para moverte, a para aprobar y d para descartar. Siempre pide confirmación.</p>}
          {cola.hasNextPage && (
            <Button className={estilos.mas} isDisabled={cola.isFetchingNextPage} onPress={() => void cola.fetchNextPage()}>
              {cola.isFetchingNextPage ? 'Cargando…' : 'Ver más reportes'}
            </Button>
          )}
        </>
      )}

      <ConfirmarAccion
        abierto={pendiente !== null}
        titulo={pendiente?.decision === 'aprobar' ? 'Aprobar este reporte' : 'Descartar este reporte'}
        descripcion={pendiente ? descripcionDe(pendiente.reporte, pendiente.decision, barrioDe(pendiente.reporte)) : null}
        etiquetaConfirmar={pendiente?.decision === 'aprobar' ? 'Aprobar reporte' : 'Descartar reporte'}
        ocupado={ocupado}
        error={error}
        alConfirmar={() => void confirmar()}
        alCancelar={() => { setPendiente(null); setError(null) }}
      />
    </div>
  )
}
