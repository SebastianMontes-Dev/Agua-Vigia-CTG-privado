// Cartagena no tiene horario de verano: UTC-5 todo el año. Se fija la zona para no depender de la del navegador.
const ZONA = 'America/Bogota'
const MINUTO = 60_000
const HORA = 60 * MINUTO
const DIA = 24 * HORA

const formatoHora = new Intl.DateTimeFormat('es-CO', { timeZone: ZONA, hour: 'numeric', minute: '2-digit', hour12: true })
const formatoDia = new Intl.DateTimeFormat('es-CO', { timeZone: ZONA, day: 'numeric', month: 'long' })
const formatoDiaConAnio = new Intl.DateTimeFormat('es-CO', { timeZone: ZONA, day: 'numeric', month: 'long', year: 'numeric' })
const formatoClaveDia = new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' })

export interface HoraPartida {
  cifra: string
  periodo: string
}

function aFecha(valor: string | Date): Date {
  return typeof valor === 'string' ? new Date(valor) : valor
}

// Intl separa «a. m.» con espacios duros según el motor; se normalizan para que el texto sea estable.
function normalizarEspacios(texto: string): string {
  return texto.replace(/[  ]/g, ' ')
}

export function formatearHora(valor: string | Date): string {
  return normalizarEspacios(formatoHora.format(aFecha(valor)))
}

/** «6:10» y «a. m.» por separado, para componer la cifra grande y su periodo en tamaños distintos. */
export function partirHora(valor: string | Date): HoraPartida {
  const partes = formatoHora.formatToParts(aFecha(valor))
  const cifra = partes.filter((p) => p.type === 'hour' || p.type === 'minute' || p.type === 'literal')
    .map((p) => p.value).join('').trim()
  const periodo = partes.find((p) => p.type === 'dayPeriod')?.value ?? ''
  return { cifra: normalizarEspacios(cifra), periodo: normalizarEspacios(periodo) }
}

function claveDia(fecha: Date): string {
  return formatoClaveDia.format(fecha)
}

/** «hoy», «ayer», «mañana» o la fecha, siempre según el calendario de Cartagena. */
export function diaRelativo(valor: string | Date, ahora: Date): string {
  const fecha = aFecha(valor)
  const clave = claveDia(fecha)
  if (clave === claveDia(ahora)) return 'hoy'
  if (clave === claveDia(new Date(ahora.getTime() - DIA))) return 'ayer'
  if (clave === claveDia(new Date(ahora.getTime() + DIA))) return 'mañana'
  const mismoAnio = clave.slice(0, 4) === claveDia(ahora).slice(0, 4)
  return (mismoAnio ? formatoDia : formatoDiaConAnio).format(fecha)
}

/** «a las 6:10 a. m.» hoy; «ayer a las…»; «el 24 de julio a las…» en otra fecha. */
export function momento(valor: string | Date, ahora: Date): string {
  const dia = diaRelativo(valor, ahora)
  const hora = formatearHora(valor)
  if (dia === 'hoy') return `a las ${hora}`
  if (dia === 'ayer' || dia === 'mañana') return `${dia} a las ${hora}`
  return `el ${dia} a las ${hora}`
}

export function haceCuanto(valor: string | Date, ahora: Date): string {
  const transcurrido = Math.max(0, ahora.getTime() - aFecha(valor).getTime())
  if (transcurrido < MINUTO) return 'hace menos de un minuto'
  if (transcurrido < HORA) return `hace ${Math.floor(transcurrido / MINUTO)} min`
  if (transcurrido < DIA) {
    const horas = Math.floor(transcurrido / HORA)
    const minutos = Math.floor((transcurrido % HORA) / MINUTO)
    return minutos === 0 ? `hace ${horas} h` : `hace ${horas} h ${minutos} min`
  }
  const dias = Math.floor(transcurrido / DIA)
  return dias === 1 ? 'hace 1 día' : `hace ${dias} días`
}

function plural(cantidad: number, singular: string, pluralTexto: string): string {
  return `${cantidad} ${cantidad === 1 ? singular : pluralTexto}`
}

/** DESIGN.md §5: «4 horas y media», nunca «4.5h». Redondea al cuarto de hora desde una hora. */
export function duracionEnPalabras(milisegundos: number): string {
  const minutosTotales = Math.round(Math.abs(milisegundos) / MINUTO)
  if (minutosTotales < 60) return plural(Math.max(1, minutosTotales), 'minuto', 'minutos')
  if (minutosTotales >= 48 * 60) return plural(Math.round(minutosTotales / (24 * 60)), 'día', 'días')
  const cuartos = Math.round(minutosTotales / 15)
  const horas = Math.floor(cuartos / 4)
  const resto = cuartos % 4
  const base = plural(horas, 'hora', 'horas')
  if (resto === 0) return base
  if (resto === 2) return `${base} y media`
  return `${base} y ${resto * 15} minutos`
}
