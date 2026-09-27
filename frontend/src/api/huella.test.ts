import { obtenerHuella } from './huella'

function almacenFalso() {
  const datos = new Map<string, string>()
  return { getItem: (k: string) => datos.get(k) ?? null, setItem: (k: string, v: string) => void datos.set(k, v) }
}

describe('obtenerHuella', () => {
  it('debeGenerarUnaHuellaDe64HexadecimalesYReutilizarla', async () => {
    const almacen = almacenFalso()
    const primera = await obtenerHuella(almacen)
    expect(primera).toMatch(/^[0-9a-f]{64}$/)
    expect(await obtenerHuella(almacen)).toBe(primera)
  })

  it('debeDarHuellasDistintasADispositivosDistintos', async () => {
    const otroDispositivo = async () => {
      vi.resetModules()
      return (await import('./huella')).obtenerHuella(almacenFalso())
    }
    expect(await otroDispositivo()).not.toBe(await otroDispositivo())
  })

  it('debeReemplazarUnaHuellaGuardadaQueNoTieneElFormato', async () => {
    const almacen = almacenFalso()
    almacen.setItem('aguavigia.huella', 'correo@ejemplo.com')
    expect(await obtenerHuella(almacen)).toMatch(/^[0-9a-f]{64}$/)
  })
})
