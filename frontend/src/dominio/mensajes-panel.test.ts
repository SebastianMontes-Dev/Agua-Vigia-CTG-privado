import { mensajeIngreso, mensajeSegundoFactor, minutos } from './mensajes-panel'

describe('mensajes del ingreso', () => {
  it('noDebeDecirCualCampoFalloConCorreoOClaveIncorrectos', () => {
    expect(mensajeIngreso({ tipo: 'credencial-invalida' }, false)).toBe('Correo o clave incorrectos.')
  })

  it('debeCulparAlCodigoCuandoLaClaveYaSeAcepto', () => {
    expect(mensajeIngreso({ tipo: 'credencial-invalida' }, true)).toMatch(/^El código no coincide/)
  })

  it('debeExplicarCadaEstadoDeCuentaYCaerAlDetalleSiEsDesconocido', () => {
    expect(mensajeIngreso({ tipo: 'no-habilitada', estado: 'PENDIENTE_APROBACION', mensaje: 'x' }, false)).toMatch(/administrador apruebe/)
    expect(mensajeIngreso({ tipo: 'no-habilitada', estado: 'SUSPENDIDA', mensaje: 'x' }, false)).toMatch(/suspendida/)
    expect(mensajeIngreso({ tipo: 'no-habilitada', estado: 'OTRO', mensaje: 'Detalle del servidor' }, false)).toBe('Detalle del servidor')
  })

  it('debeDecirLaEsperaRealEnMinutosRedondeadosHaciaArriba', () => {
    expect(mensajeIngreso({ tipo: 'bloqueada', segundos: 840 }, false)).toContain('14 minutos')
    expect(mensajeIngreso({ tipo: 'esperar', segundos: 30 }, false)).toContain('1 minuto')
    expect(mensajeIngreso({ tipo: 'esperar', segundos: null }, false)).toContain('unos minutos')
    expect(minutos(61)).toBe('2 minutos')
  })

  it('debeRemitirASeguridadSiLaCuentaYaTieneSegundoFactor', () => {
    expect(mensajeSegundoFactor({ tipo: 'ya-activo' })).toMatch(/Seguridad/)
  })
})
