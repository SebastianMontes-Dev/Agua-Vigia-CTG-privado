export interface Paginacion {
  total: number | null
  paginas: number | null
  pagina: number
  hayMas: boolean
}

function entero(valor: string | null): number | null {
  if (valor === null || valor.trim() === '') return null
  const numero = Number(valor)
  return Number.isInteger(numero) && numero >= 0 ? numero : null
}

/** docs/api: la respuesta es un arreglo y la paginación viaja en cabeceras; `Link rel="next"` decide si hay más. */
export function leerPaginacion(cabeceras: Headers, paginaPedida: number): Paginacion {
  const total = entero(cabeceras.get('X-Total-Count'))
  const paginas = entero(cabeceras.get('X-Total-Pages'))
  const pagina = entero(cabeceras.get('X-Page')) ?? paginaPedida
  const enlace = cabeceras.get('Link') ?? ''
  const hayMas = /rel="?next"?/.test(enlace) || (paginas !== null && pagina + 1 < paginas)
  return { total, paginas, pagina, hayMas }
}
