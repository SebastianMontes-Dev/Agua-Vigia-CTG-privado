import { describe, expect, it } from 'vitest'
import { seccionActiva, seccionesPermitidas } from './PanelMarco'

describe('seccionesPermitidas', () => {
  it('debeMostrarSoloLoQueDicenLosPermisosNoElRol', () => {
    expect(seccionesPermitidas(['VER_PANEL', 'CONFIGURAR_SEGUNDO_FACTOR']).map((s) => s.texto)).toEqual(['Reportes', 'Mi seguridad'])
  })

  it('debeDejarSiempreLaSeguridadDeLaPropiaCuenta', () => {
    expect(seccionesPermitidas([]).map((s) => s.texto)).toEqual(['Mi seguridad'])
    expect(seccionesPermitidas(undefined).map((s) => s.texto)).toEqual(['Mi seguridad'])
  })
})

describe('seccionActiva', () => {
  it('debeMarcarReportesSoloEnLaRaizDelPanel', () => {
    expect(seccionActiva('/panel', '/panel')).toBe(true)
    expect(seccionActiva('/panel/', '/panel')).toBe(true)
    expect(seccionActiva('/panel/seguridad', '/panel')).toBe(false)
    expect(seccionActiva('/panel/seguridad', '/panel/seguridad')).toBe(true)
  })
})
