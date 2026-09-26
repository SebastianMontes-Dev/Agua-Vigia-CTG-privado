import { webcrypto } from 'node:crypto'
import { describe, expect, it } from 'vitest'
import type { FeatureCollection, MultiPolygon } from 'geojson'
import { fechaCartagena, habitantes, unirSectores, verificacionAntigua } from './sectores'
import { crearProveedorHuella, CLAVE_HUELLA } from './huella'
import { FalloCiudadano, mensajeFallo } from '../api/ciudadano'
import { normalizarError } from '../api/cliente'
describe('consulta ciudadana fiel al contrato', () => {
  it('une por id sin modificar MultiPolygon ni fabricar estado', () => {
    const geometria: FeatureCollection<MultiPolygon> = { type: 'FeatureCollection', features: [{ type: 'Feature', id: 'zona-industrial', properties: { nombre: 'viejo' }, geometry: { type: 'MultiPolygon', coordinates: [[[[-75, 10], [-75, 11], [-74, 10], [-75, 10]]]] } }] }
    const unido = unirSectores(geometria, [{ id: 'otro', estado: 'CON_SERVICIO' }, { id: 'zona-industrial', estado: null }])
    expect(unido.features[0]?.geometry).toBe(geometria.features[0]?.geometry)
    expect(unido.features[0]?.properties?.estado).toBeNull()
    expect(unido.features[0]?.properties?.id).toBe('zona-industrial')
    expect(geometria.features[0]?.properties?.id).toBeUndefined()
  })
  it('declara antigua la verificación a las 24 horas sin cambiar estado', () => {
    const sector = { estado: 'SIN_SERVICIO' as const, verificadoEn: '2026-09-25T12:00:00Z' }
    expect(verificacionAntigua(sector, Date.parse('2026-09-26T11:59:59Z'))).toBe(false)
    expect(verificacionAntigua(sector, Date.parse('2026-09-26T12:00:00Z'))).toBe(true)
    expect(sector.estado).toBe('SIN_SERVICIO')
    expect(verificacionAntigua({ estado: null })).toBe(false)
  })
  it('distingue población desconocida de cero y usa hora de Cartagena', () => {
    expect(habitantes(null)).toBe('Sin dato censal')
    expect(habitantes(0)).toBe('0 habitantes')
    expect(fechaCartagena('2026-09-25T17:00:00Z')).toContain('12:00')
    expect(fechaCartagena(null)).toBe('Sin fecha registrada')
  })
  it('genera una sola huella persistente de 64 hex incluso concurrentemente', async () => {
    const valores = new Map<string, string>()
    const almacen = { getItem: (clave: string) => valores.get(clave) ?? null, setItem: (clave: string, valor: string) => { valores.set(clave, valor) } }
    const proveedor = crearProveedorHuella(almacen, webcrypto as unknown as Crypto)
    const huellas = await Promise.all([proveedor(), proveedor(), proveedor()])
    expect(huellas[0]).toMatch(/^[a-f0-9]{64}$/)
    expect(new Set(huellas).size).toBe(1)
    expect(valores.get(CLAVE_HUELLA)).toBe(huellas[0])
    expect(await crearProveedorHuella(almacen, webcrypto as unknown as Crypto)()).toBe(huellas[0])
  })
  it('no elude el cupo con una huella nueva si no puede persistirla', async () => {
    const proveedor = crearProveedorHuella({ getItem: () => { throw new Error('bloqueado') }, setItem: () => {} }, webcrypto as unknown as Crypto)
    await expect(proveedor()).rejects.toThrow('bloqueado')
  })
  for (const [tipo, esperado] of [['limite-reportes-excedido', 'tres reportes'], ['peticion-invalida', 'Elige el barrio a mano'], ['recurso-no-encontrado', 'no está disponible'], ['archivo-demasiado-grande', 'menos de 10 MB']] as const) {
    it(`reacciona por type ${tipo} aunque title y detail mientan`, () => {
      const error = normalizarError(new Response('', { status: 400 }), { type: `https://aguavigia.example/errores/${tipo}`, title: 'otro título', detail: 'otro detalle' })
      expect(mensajeFallo(new FalloCiudadano(error))).toContain(esperado)
    })
  }
  it('un fallo de transporte no promete reporte recibido', () => {
    expect(mensajeFallo(new FalloCiudadano(normalizarError(null, null)), true)).toContain('No pudimos comprobar si llegó')
  })
  it('una foto inválida conserva el reporte y pide cambiar la foto', () => {
    const error = normalizarError(new Response('', { status: 400 }), { type: 'https://aguavigia.example/errores/peticion-invalida' })
    expect(mensajeFallo(new FalloCiudadano(error), false, true)).toContain('elige otra foto')
    expect(mensajeFallo(new FalloCiudadano(error), false, true)).toContain('reporte sigue guardado')
  })
})
