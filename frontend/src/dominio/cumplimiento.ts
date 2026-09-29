import type { components } from '../api/generado/esquema'
import { formatearNumero } from './formato'

export type Indice = components['schemas']['IndiceCumplimientoRespuesta']
export type PuntoSerie = components['schemas']['PuntoSerieRespuesta']

export function indiceCompleto(indice: Indice | undefined): indice is Indice & {
  duracionPrometidaSegundos: number
  duracionRealSegundos: number
  desviacionSegundos: number
  porcentajeCumplimiento: number
} {
  return !!indice && Number.isFinite(indice.duracionPrometidaSegundos)
    && Number.isFinite(indice.duracionRealSegundos) && Number.isFinite(indice.desviacionSegundos)
    && Number.isFinite(indice.porcentajeCumplimiento)
}

export function conclusionCumplimiento(indice: Indice): string | null {
  if (!indiceCompleto(indice)) return null
  const diferencia = indice.desviacionSegundos
  if (diferencia === 0) return 'En total, los cortes cerrados duraron lo anunciado.'
  return `En total, los cortes cerrados duraron ${duracionAcumulada(Math.abs(diferencia))} ${diferencia > 0 ? 'más' : 'menos'} de lo anunciado.`
}

/** Las duraciones de un día o más omiten minutos; las menores conservan horas y minutos. */
export function duracionAcumulada(segundos: number): string {
  const minutos = Math.round(Math.abs(segundos) / 60)
  const dias = Math.floor(minutos / 1440)
  const horas = Math.floor((minutos % 1440) / 60)
  const resto = dias ? 0 : minutos % 60
  const partes = [
    dias ? `${formatearNumero(dias)} ${dias === 1 ? 'día' : 'días'}` : '',
    horas ? `${formatearNumero(horas)} ${horas === 1 ? 'hora' : 'horas'}` : '',
    resto ? `${formatearNumero(resto)} ${resto === 1 ? 'minuto' : 'minutos'}` : '',
  ].filter(Boolean)
  return partes.length ? partes.join(' y ') : '0 minutos'
}

export function diferenciaBreve(segundos: number | undefined): string {
  if (segundos === undefined) return 'Sin dato'
  if (segundos === 0) return 'Lo anunciado'
  const minutos = Math.round(Math.abs(segundos) / 60)
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  const partes = [horas ? `${formatearNumero(horas)} h` : '', resto ? `${formatearNumero(resto)} min` : ''].filter(Boolean)
  return `${partes.length ? partes.join(' ') : '0 min'} ${segundos > 0 ? 'más' : 'menos'}`
}

export function ordenarSerie(serie: readonly PuntoSerie[]): PuntoSerie[] {
  return [...serie].sort((a, b) => (a.periodo ?? '').localeCompare(b.periodo ?? ''))
}

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']

export function mesEnPalabras(periodo?: string): string {
  const partes = /^(\d{4})-(0[1-9]|1[0-2])$/.exec(periodo ?? '')
  return partes ? `${MESES[Number(partes[2]) - 1]} de ${partes[1]}` : 'Mes sin fecha'
}

function fechaValida(dia: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dia)) return false
  const fecha = new Date(`${dia}T00:00:00Z`)
  return !Number.isNaN(fecha.getTime()) && fecha.toISOString().slice(0, 10) === dia
}

/** La serie usa límite superior inclusivo en el backend; se conserva todo el día de Cartagena. */
export function limiteSerieCartagena(dia: string, final = false): string | null {
  if (!fechaValida(dia)) return null
  const inicio = Date.parse(`${dia}T05:00:00Z`)
  return new Date(inicio + (final ? 86_399_999 : 0)).toISOString()
}

export function rangoSerieValido(desde?: string, hasta?: string): boolean {
  if (desde && !fechaValida(desde)) return false
  if (hasta && !fechaValida(hasta)) return false
  return !desde || !hasta || desde <= hasta
}
