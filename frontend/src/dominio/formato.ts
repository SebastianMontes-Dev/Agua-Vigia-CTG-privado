// Las fechas y horas viven en tiempo.ts; aquí solo cifras.
const LOCALE = 'es-CO'

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

export function formatearNumero(valor: number, decimalesMaximos?: number): string {
  if (decimalesMaximos === undefined || decimalesMaximos <= 0) {
    return formatoEntero.format(valor)
  }
  return obtenerFormateadorDecimal(decimalesMaximos).format(valor)
}

export function formatearPorcentaje(valor: number): string {
  return formatoPorcentaje.format(valor / 100)
}
