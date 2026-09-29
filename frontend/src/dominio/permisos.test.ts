import { permisosDe, seccionActiva, seccionesPermitidas } from './permisos'

describe('permisos del panel', () => {
  it('debeMostrarAlObservadorSoloLasSeccionesDeLectura', () => {
    const secciones = seccionesPermitidas(permisosDe(['VER_PANEL', 'CONFIGURAR_SEGUNDO_FACTOR']))
    expect(secciones.map((seccion) => seccion.texto)).toEqual(['Moderación', 'Cortes', 'Ingesta', 'Seguridad'])
  })

  it('debeSumarCuentasYAuditoriaSoloConSusPermisos', () => {
    const secciones = seccionesPermitidas(permisosDe(['VER_PANEL', 'GESTIONAR_USUARIOS', 'VER_AUDITORIA']))
    expect(secciones.map((seccion) => seccion.texto)).toContain('Cuentas')
    expect(secciones.map((seccion) => seccion.texto)).toContain('Auditoría')
  })

  it('debeIgnorarPermisosDesconocidosYNoPintarNadaSinVerPanel', () => {
    expect(seccionesPermitidas(permisosDe(['PERMISO_NUEVO', 'CONFIGURAR_SEGUNDO_FACTOR']))).toEqual([])
    expect(seccionesPermitidas(permisosDe(undefined))).toEqual([])
  })

  it('debeResaltarModeracionSoloEnLaRaizDelPanel', () => {
    expect(seccionActiva('/panel', '/panel')).toBe(true)
    expect(seccionActiva('/panel/cortes', '/panel')).toBe(false)
    expect(seccionActiva('/panel/cortes', '/panel/cortes')).toBe(true)
    expect(seccionActiva('/panel/cortesias', '/panel/cortes')).toBe(false)
  })
})
