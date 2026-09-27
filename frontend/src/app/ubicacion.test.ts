import { olvidarUbicacion, pedirUbicacion, recordarUbicacion, ubicacionPara } from './ubicacion'

const CODIGOS = { PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 }

function responderPosicion(latitude: number, longitude: number, accuracy: number): Geolocation {
  return {
    getCurrentPosition: (exito: PositionCallback) => exito({ coords: { latitude, longitude, accuracy } } as GeolocationPosition),
  } as unknown as Geolocation
}

function responderError(code: number): Geolocation {
  return {
    getCurrentPosition: (_exito: PositionCallback, fallo?: PositionErrorCallback | null) =>
      fallo?.({ code, ...CODIGOS } as GeolocationPositionError),
  } as unknown as Geolocation
}

describe('pedirUbicacion', () => {
  it('debeEntregarLaCoordenadaConNombre', async () => {
    expect(await pedirUbicacion(responderPosicion(10.4, -75.5, 30)))
      .toEqual({ tipo: 'ubicada', coordenada: { latitud: 10.4, longitud: -75.5 } })
  })

  it('debeRechazarUnaUbicacionDemasiadoImprecisaParaElegirBarrio', async () => {
    expect(await pedirUbicacion(responderPosicion(10.4, -75.5, 2400.4))).toEqual({ tipo: 'imprecisa', metros: 2400 })
  })

  it('debeDistinguirElPermisoNegadoDelTiempoAgotadoYDeLaFaltaDeSenal', async () => {
    expect(await pedirUbicacion(responderError(1))).toEqual({ tipo: 'denegada' })
    expect(await pedirUbicacion(responderError(3))).toEqual({ tipo: 'agotada' })
    expect(await pedirUbicacion(responderError(2))).toEqual({ tipo: 'no-disponible' })
  })

  it('debeResponderNoDisponibleSinApiDeGeolocalizacion', async () => {
    expect(await pedirUbicacion(undefined)).toEqual({ tipo: 'no-disponible' })
  })
})

describe('ubicacionPara', () => {
  afterEach(olvidarUbicacion)

  it('debeAcompanarSoloElReporteDelBarrioDondeSeLocalizo', () => {
    recordarUbicacion('manga', { latitud: 10.41, longitud: -75.53 })
    expect(ubicacionPara('manga')).toEqual({ latitud: 10.41, longitud: -75.53 })
    expect(ubicacionPara('armenia')).toBeNull()
  })

  it('noDebeTenerUbicacionAntesDeQueLaPersonaLaComparta', () => {
    expect(ubicacionPara('manga')).toBeNull()
  })
})
