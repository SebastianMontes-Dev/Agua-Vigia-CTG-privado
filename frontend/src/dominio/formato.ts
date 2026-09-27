const ZONA_HORARIA = 'America/Bogota'
const LOCALE = 'es-CO'
const TEXTO_SIN_FECHA = 'Sin fecha de registro'

const formatoEntero = new Intl.NumberFormat(LOCALE, {
  maximumFractionDigits: 0,
})

const formateadoresDecimales = new Map<number, Intl.NumberFormat>()

function obtenerFormateadorDecimal(decimales: number): Intl.NumberFormat {
  let formateador = formateadoresDecimales.get(decimales)
  if (!formateador) {
    formateador = new Intl.NumberFormat(LOCALE, {
      minimumFractionDigits: 0,
      maximumFractionDigits: decimales,
    })
    formateadoresDecimales.set(decimales, formateador)
  }
  return formateador
}

const formatoPorcentaje = new Intl.NumberFormat(LOCALE, {
  style: 'percent',
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
})

const formatoHora = new Intl.DateTimeFormat(LOCALE, {
  timeZone: ZONA_HORARIA,
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

const formatoClaveDia = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA_HORARIA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const formatoMismoAno = new Intl.DateTimeFormat(LOCALE, {
  timeZone: ZONA_HORARIA,
  day: 'numeric',
  month: 'short',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

const formatoOtroAno = new Intl.DateTimeFormat(LOCALE, {
  timeZone: ZONA_HORARIA,
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

export function formatearNumero(valor: number, decimalesMaximos?: number): string {
  if (decimalesMaximos === undefined || decimalesMaximos <= 0) {
    return formatoEntero.format(valor)
  }
  return obtenerFormateadorDecimal(decimalesMaximos).format(valor)
}

export function formatearPorcentaje(valor: number): string {
  return formatoPorcentaje.format(valor / 100)
}

export function formatearHora(iso: string | null | undefined): string {
  if (!iso) {
    return TEXTO_SIN_FECHA
  }
  const fecha = new Date(iso)
  if (Number.isNaN(fecha.getTime())) {
    return TEXTO_SIN_FECHA
  }
  return formatoHora.format(fecha)
}

export function formatearMomento(iso: string | null | undefined, ahora: Date): string {
  if (!iso) {
    return TEXTO_SIN_FECHA
  }
  const fecha = new Date(iso)
  if (Number.isNaN(fecha.getTime())) {
    return TEXTO_SIN_FECHA
  }

  const claveFecha = formatoClaveDia.format(fecha)
  const claveAhora = formatoClaveDia.format(ahora)

  if (claveFecha === claveAhora) {
    return formatoHora.format(fecha)
  }

  const anoFecha = claveFecha.slice(0, 4)
  const anoAhora = claveAhora.slice(0, 4)

  if (anoFecha === anoAhora) {
    return formatoMismoAno.format(fecha)
  }

  return formatoOtroAno.format(fecha)
}
