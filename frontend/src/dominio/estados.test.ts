import { ORDEN_ESTADOS, presentarEstado } from './estados'

describe('presentarEstado', () => {
  it('debeMostrarSinDatosCuandoElEstadoEsNulo', () => {
    const presentacion = presentarEstado(null)
    expect(presentacion.clave).toBe('SIN_DATOS')
    expect(presentacion.texto).toBe('Sin datos verificados')
    expect(presentacion.variableColor).toBeNull()
  })

  it('debeTratarUndefinedIgualQueNulo', () => {
    expect(presentarEstado(undefined)).toEqual(presentarEstado(null))
  })

  it('debeDarleACadaEstadoSuPropioColorYTexto', () => {
    const colores = ORDEN_ESTADOS.map((estado) => presentarEstado(estado).variableColor)
    const textos = ORDEN_ESTADOS.map((estado) => presentarEstado(estado).texto)
    expect(new Set(colores).size).toBe(4)
    expect(new Set(textos).size).toBe(4)
    expect(colores).not.toContain(null)
  })

  it('debeOrdenarLosEstadosPorSeveridadSegunAdr061', () => {
    expect(ORDEN_ESTADOS).toEqual(['SIN_SERVICIO', 'CORTE_PROGRAMADO', 'PRESION_BAJA', 'CON_SERVICIO'])
  })
})
