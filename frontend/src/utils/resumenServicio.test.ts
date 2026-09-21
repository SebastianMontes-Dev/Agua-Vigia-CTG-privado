import { describe, expect, it } from 'vitest'
import type { EstadoServicio, Sector } from '../types/tipos-dominio'
import { resumirServicio } from './resumenServicio'

const sector = (id: string, estado: EstadoServicio | null): Sector => ({
  id,
  nombre: id,
  estado,
  actualizadoEn: estado ? '2026-09-20T12:00:00Z' : null,
})

describe('resumen del servicio para la barra y las tarjetas', () => {
  it('sin sectores no hay datos ni porcentaje: se muestra «calculando», no un número', () => {
    expect(resumirServicio([])).toEqual({ datosDisponibles: false, porcentajeOperativo: null })
  })

  it('un sector sin dato verificado nunca cuenta como «con servicio»', () => {
    const sectores = [sector('a', null), sector('b', null)]

    expect(resumirServicio(sectores)).toEqual({ datosDisponibles: false, porcentajeOperativo: null })
  })

  it('el porcentaje se calcula solo sobre los sectores con dato verificado', () => {
    const sectores = [
      sector('a', 'CON_SERVICIO'),
      sector('b', 'CON_SERVICIO'),
      sector('c', 'SIN_SERVICIO'),
      sector('d', null),
    ]

    expect(resumirServicio(sectores)).toEqual({ datosDisponibles: true, porcentajeOperativo: 67 })
  })

  it('presión baja y corte programado no cuentan como operativos', () => {
    const sectores = [sector('a', 'CON_SERVICIO'), sector('b', 'PRESION_BAJA'), sector('c', 'CORTE_PROGRAMADO')]

    expect(resumirServicio(sectores).porcentajeOperativo).toBe(33)
  })

  it('con todos los sectores sin servicio el porcentaje es 0, no null', () => {
    expect(resumirServicio([sector('a', 'SIN_SERVICIO')])).toEqual({ datosDisponibles: true, porcentajeOperativo: 0 })
  })
})
