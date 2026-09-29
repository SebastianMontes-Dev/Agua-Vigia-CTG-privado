export const PERMISOS = [
  'VER_PANEL',
  'MODERAR_REPORTES',
  'GESTIONAR_CORTES',
  'REVISAR_INGESTA',
  'GESTIONAR_USUARIOS',
  'VER_AUDITORIA',
  'CONFIGURAR_SEGUNDO_FACTOR',
] as const

export type Permiso = typeof PERMISOS[number]

export type RutaPanel = '/panel' | '/panel/cortes' | '/panel/ingesta' | '/panel/cuentas' | '/panel/auditoria' | '/panel/seguridad'

export interface SeccionPanel {
  to: RutaPanel
  texto: string
  permiso: Permiso
}

/** Permiso para ver cada sección (panel-veedor.md); las acciones dentro de ella piden el suyo. */
export const SECCIONES_PANEL: readonly SeccionPanel[] = [
  { to: '/panel', texto: 'Moderación', permiso: 'VER_PANEL' },
  { to: '/panel/cortes', texto: 'Cortes', permiso: 'VER_PANEL' },
  { to: '/panel/ingesta', texto: 'Ingesta', permiso: 'VER_PANEL' },
  { to: '/panel/cuentas', texto: 'Cuentas', permiso: 'GESTIONAR_USUARIOS' },
  { to: '/panel/auditoria', texto: 'Auditoría', permiso: 'VER_AUDITORIA' },
  { to: '/panel/seguridad', texto: 'Seguridad', permiso: 'VER_PANEL' },
]

export function esPermiso(valor: unknown): valor is Permiso {
  return typeof valor === 'string' && PERMISOS.some((permiso) => permiso === valor)
}

/** La interfaz se pinta con los permisos efectivos, nunca con el rol (plan §6.7). */
export function permisosDe(valores: readonly string[] | undefined): ReadonlySet<Permiso> {
  return new Set((valores ?? []).filter(esPermiso))
}

export function seccionesPermitidas(permisos: ReadonlySet<Permiso>): SeccionPanel[] {
  return SECCIONES_PANEL.filter((seccion) => permisos.has(seccion.permiso))
}

/** Un `/panel/...` abierto (o una subruta futura) resalta su sección; `/panel` a secas solo se resalta a sí mismo. */
export function seccionActiva(ruta: string, seccion: RutaPanel): boolean {
  if (seccion === '/panel') return ruta === '/panel' || ruta === '/panel/'
  return ruta === seccion || ruta.startsWith(`${seccion}/`)
}
