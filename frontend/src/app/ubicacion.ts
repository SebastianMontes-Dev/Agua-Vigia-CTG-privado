import type { Coordenada } from '../dominio/ubicacion'

export type ResultadoUbicacion =
  | { tipo: 'ubicada'; coordenada: Coordenada }
  | { tipo: 'imprecisa'; metros: number }
  | { tipo: 'denegada' }
  | { tipo: 'no-disponible' }
  | { tipo: 'agotada' }

// Un barrio de Cartagena mide pocos cientos de metros: con más error que esto, el barrio sería una adivinanza.
export const PRECISION_MAXIMA_M = 1000

const OPCIONES: PositionOptions = { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 }

export function hayGeolocalizacion(): boolean {
  return typeof navigator !== 'undefined' && 'geolocation' in navigator
}

export function pedirUbicacion(geolocalizacion: Geolocation | undefined = hayGeolocalizacion() ? navigator.geolocation : undefined): Promise<ResultadoUbicacion> {
  if (!geolocalizacion) return Promise.resolve({ tipo: 'no-disponible' })
  return new Promise((resolver) => {
    geolocalizacion.getCurrentPosition(
      ({ coords }) => resolver(coords.accuracy > PRECISION_MAXIMA_M
        ? { tipo: 'imprecisa', metros: Math.round(coords.accuracy) }
        : { tipo: 'ubicada', coordenada: { latitud: coords.latitude, longitud: coords.longitude } }),
      (error) => resolver(
        error.code === error.PERMISSION_DENIED ? { tipo: 'denegada' }
          : error.code === error.TIMEOUT ? { tipo: 'agotada' }
            : { tipo: 'no-disponible' },
      ),
      OPCIONES,
    )
  })
}

// Solo en memoria y solo para esta visita: nada de la ubicación se guarda en el dispositivo.
let compartida: { sectorId: string; coordenada: Coordenada } | null = null

export function recordarUbicacion(sectorId: string, coordenada: Coordenada): void {
  compartida = { sectorId, coordenada }
}

/** RF007: la coordenada acompaña el reporte solo si es del barrio en el que se localizó a la persona. */
export function ubicacionPara(sectorId: string): Coordenada | null {
  return compartida?.sectorId === sectorId ? compartida.coordenada : null
}

export function olvidarUbicacion(): void {
  compartida = null
}
