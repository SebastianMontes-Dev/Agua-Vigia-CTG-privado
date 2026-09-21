import type { Sector } from '../types/tipos-dominio'

export interface ResumenServicio {
  /** Hay al menos un sector con estado verificado. Sin eso las cifras no significan nada. */
  datosDisponibles: boolean
  /** Sectores «con servicio» sobre los que tienen estado verificado; null si ninguno lo tiene. */
  porcentajeOperativo: number | null
}

/**
 * Un sector sin estado (`null`) no se cuenta ni como operativo ni como afectado: el backend los crea
 * desde el GeoJSON sin que nadie los haya verificado, y suponerlos «con servicio» publicaría un dato
 * que nadie sustenta (CLAUDE.md § Ética de datos). Presión baja y corte programado tampoco cuentan
 * como operativos: la cifra es deliberadamente conservadora.
 */
export function resumirServicio(sectores: Sector[]): ResumenServicio {
  const verificados = sectores.filter((sector) => sector.estado !== null)
  if (verificados.length === 0) return { datosDisponibles: false, porcentajeOperativo: null }

  const operativos = verificados.filter((sector) => sector.estado === 'CON_SERVICIO').length
  return {
    datosDisponibles: true,
    porcentajeOperativo: Math.round((operativos / verificados.length) * 100),
  }
}
