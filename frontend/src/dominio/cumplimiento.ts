import type { components } from '../api/generado/esquema'
import { formatearNumero, formatearPorcentaje } from './formato'

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
  const resultado = diferencia > 0
    ? `Los cortes duraron ${duracionAcumulada(diferencia)} más de lo prometido`
    : diferencia < 0
      ? `Los cortes terminaron ${duracionAcumulada(-diferencia)} antes de lo prometido`
      : 'Los cortes duraron lo prometido'
  return `Prometieron ${duracionAcumulada(indice.duracionPrometidaSegundos)}. Fueron ${duracionAcumulada(indice.duracionRealSegundos)}. ${resultado}: ${formatearPorcentaje(indice.porcentajeCumplimiento)} de cumplimiento`
}

/** Los agregados pueden durar meses: no redondear a días enteros y ocultar la diferencia. */
export function duracionAcumulada(segundos: number): string {
  const minutos = Math.round(Math.abs(segundos) / 60)
  const dias = Math.floor(minutos / 1440)
  const horas = Math.floor((minutos % 1440) / 60)
  const resto = minutos % 60
  if (!dias && horas && resto === 30) return `${formatearNumero(horas)} ${horas === 1 ? 'hora' : 'horas'} y media`
  const partes = [
    dias ? `${formatearNumero(dias)} ${dias === 1 ? 'día' : 'días'}` : '',
    horas ? `${formatearNumero(horas)} ${horas === 1 ? 'hora' : 'horas'}` : '',
    resto ? `${formatearNumero(resto)} ${resto === 1 ? 'minuto' : 'minutos'}` : '',
  ].filter(Boolean)
  return partes.length ? partes.join(' y ') : '0 minutos'
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
