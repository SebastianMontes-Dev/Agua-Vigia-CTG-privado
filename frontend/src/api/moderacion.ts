import type { components } from './generado/esquema'
import { api } from './cliente'
import { intentar } from './panel'

export type ReportePendiente = components['schemas']['ReporteModeracionRespuesta']
export type Decision = 'aprobar' | 'descartar'

/** Un reporte descartado deja de contar para el consenso, pero sigue gastando el cupo de su dispositivo (docs/api/panel-veedor.md). */
export function decidirReporte(id: string, decision: Decision) {
  return intentar(() => decision === 'aprobar'
    ? api.PATCH('/api/veedor/reportes/{id}/aprobar', { params: { path: { id } } })
    : api.PATCH('/api/veedor/reportes/{id}/descartar', { params: { path: { id } } }))
}
