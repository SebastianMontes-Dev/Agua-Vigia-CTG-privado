import { leerPaginacion } from './paginacion'

describe('leerPaginacion', () => {
  it('debeLeerLasCabecerasYSaberSiHayOtraPagina', () => {
    const cabeceras = new Headers({ 'X-Total-Count': '23', 'X-Total-Pages': '3', 'X-Page': '1', Link: '</api/x?pagina=2>; rel="next"' })
    expect(leerPaginacion(cabeceras, 1)).toEqual({ total: 23, paginas: 3, pagina: 1, hayMas: true })
  })

  it('noDebeInventarTotalesCuandoFaltanLasCabeceras', () => {
    expect(leerPaginacion(new Headers(), 0)).toEqual({ total: null, paginas: null, pagina: 0, hayMas: false })
  })

  it('debeReconocerLaUltimaPagina', () => {
    const cabeceras = new Headers({ 'X-Total-Count': '3', 'X-Total-Pages': '1', 'X-Page': '0' })
    expect(leerPaginacion(cabeceras, 0).hayMas).toBe(false)
  })
})
