import { barriosConNovedades, buscarSectores, contarPorEstado, nombreLegible, type Sector } from './sectores'
import { compararCorte, estaAbierto, origenEnPalabras, promesaVencida } from './cortes'
import { sinVerificacionReciente } from './frescura'

const SECTORES: Sector[] = [
  { id: 'bocagrande', nombre: 'BOCAGRANDE', estado: 'PRESION_BAJA', actualizadoEn: '2026-09-26T10:00:00Z' },
  { id: 'castillogrande', nombre: 'CASTILLOGRANDE', estado: 'SIN_SERVICIO', actualizadoEn: '2026-09-26T09:00:00Z' },
  { id: 'el-bosque', nombre: 'EL BOSQUE', estado: 'CON_SERVICIO', actualizadoEn: '2026-09-26T08:00:00Z' },
  { id: 'manga', nombre: 'MANGA', estado: null, actualizadoEn: null },
  { id: 'la-maria', nombre: 'LA MARÍA', estado: 'SIN_SERVICIO', actualizadoEn: '2026-09-26T11:00:00Z' },
  { id: 'bosquecito', nombre: 'BOSQUECITO', estado: 'CORTE_PROGRAMADO', actualizadoEn: '2026-09-26T07:00:00Z' },
]

describe('sectores', () => {
  it('debePresentarElNombreOficialEnTipoOracion', () => {
    expect(nombreLegible('VILLAS DE LA CANDELARIA')).toBe('Villas de la Candelaria')
    expect(nombreLegible('PARAISO II')).toBe('Paraiso II')
    expect(nombreLegible('JUAN XXIII')).toBe('Juan XXIII')
    expect(nombreLegible('VILLA')).toBe('Villa')
    expect(nombreLegible('EL BOSQUE')).toBe('El Bosque')
    expect(nombreLegible(undefined)).toBe('Barrio sin nombre')
  })

  it('debeBuscarSinTildesNiMayusculasYPonerPrimeroLoQueEmpiezaPorElTermino', () => {
    expect(buscarSectores(SECTORES, 'maria').map((s) => s.id)).toEqual(['la-maria'])
    expect(buscarSectores(SECTORES, 'bosq').map((s) => s.id)).toEqual(['bosquecito', 'el-bosque'])
    expect(buscarSectores(SECTORES, '   ')).toEqual([])
    expect(buscarSectores(SECTORES, 'zzz')).toEqual([])
  })

  it('debeContarElEstadoNuloComoSinDatosYNuncaComoConServicio', () => {
    expect(contarPorEstado(SECTORES)).toEqual({
      SIN_SERVICIO: 2, CORTE_PROGRAMADO: 1, PRESION_BAJA: 1, CON_SERVICIO: 1, SIN_DATOS: 1,
    })
  })

  it('debeOrdenarLasNovedadesPorGravedadYLuegoPorCambioMasReciente', () => {
    expect(barriosConNovedades(SECTORES).map((s) => s.id))
      .toEqual(['la-maria', 'castillogrande', 'bosquecito', 'bocagrande'])
  })
})

describe('cortes', () => {
  const AHORA = new Date('2026-09-26T15:00:00Z')
  const abierto = { inicio: '2026-09-26T11:00:00Z', finPrometido: '2026-09-26T14:00:00Z', estado: 'CONFIRMADO' }

  it('debeReconocerUnCorteAbiertoYSuPromesaVencida', () => {
    expect(estaAbierto(abierto)).toBe(true)
    expect(promesaVencida(abierto, AHORA)).toBe(true)
    expect(promesaVencida({ ...abierto, finPrometido: '2026-09-26T18:00:00Z' }, AHORA)).toBe(false)
  })

  it('debeCompararLoPrometidoConLoQueDuroEnPalabras', () => {
    const pasado = compararCorte({ ...abierto, finPrometido: '2026-09-26T13:00:00Z', finReal: '2026-09-26T19:00:00Z', estado: 'RESTABLECIDO' })
    expect(pasado).toMatchObject({ prometido: '2 horas', real: '8 horas', diferencia: 'Se pasó 6 horas' })
    const antes = compararCorte({ ...abierto, finPrometido: '2026-09-26T15:00:00Z', finReal: '2026-09-26T14:30:00Z', estado: 'RESTABLECIDO' })
    expect(antes?.diferencia).toBe('Terminó 30 minutos antes')
    expect(compararCorte(abierto)).toBeNull()
  })

  it('debeAtribuirSoloLosOrigenesConocidos', () => {
    expect(origenEnPalabras('OFICIAL_ACUACAR')).toBe('Aviso oficial de Acuacar')
    expect(origenEnPalabras('OTRO')).toBeNull()
    expect(origenEnPalabras(undefined)).toBeNull()
  })
})

describe('frescura', () => {
  const AHORA = new Date('2026-09-26T15:00:00Z')

  it('debeAdvertirSoloDespuesDe24HorasSinVerificacion', () => {
    expect(sinVerificacionReciente('2026-09-25T15:30:00Z', AHORA)).toBe(false)
    expect(sinVerificacionReciente('2026-09-25T14:30:00Z', AHORA)).toBe(true)
    expect(sinVerificacionReciente(null, AHORA)).toBe(false)
  })
})
