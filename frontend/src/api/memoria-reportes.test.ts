import { esPropio, recordarConfirmado, recordarPropio, yaConfirmado } from './memoria-reportes'

function almacen(): Pick<Storage, 'getItem' | 'setItem'> {
  const datos = new Map<string, string>()
  return { getItem: (clave) => datos.get(clave) ?? null, setItem: (clave, valor) => { datos.set(clave, valor) } }
}

describe('memoria de reportes', () => {
  it('debeDistinguirElReportePropioDelConfirmado', () => {
    const local = almacen()
    recordarPropio('r1', local)
    recordarConfirmado('r2', local)
    expect(esPropio('r1', local)).toBe(true)
    expect(yaConfirmado('r1', local)).toBe(false)
    expect(yaConfirmado('r2', local)).toBe(true)
  })

  it('debeConservarSoloLosCincuentaMasRecientes', () => {
    const local = almacen()
    for (let i = 0; i < 51; i++) recordarPropio(`r${i}`, local)
    expect(esPropio('r0', local)).toBe(false)
    expect(esPropio('r50', local)).toBe(true)
  })

  it('debeRecordarEnMemoriaSiElAlmacenamientoEstaBloqueado', () => {
    recordarPropio('r7', null)
    expect(esPropio('r7', null)).toBe(true)
    expect(esPropio('r7', almacen())).toBe(false)
  })

  it('debeIgnorarUnValorDanadoSinFallar', () => {
    const local = almacen()
    local.setItem('aguavigia.reportes', '{no es json')
    expect(esPropio('r1', local)).toBe(false)
  })
})
