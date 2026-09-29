import { create } from 'qrcode'
import { agruparSecreto } from '../pantallas/panel/SegundoFactor'
import { trazoQr } from './CodigoQr'

describe('código QR del segundo factor', () => {
  const uri = 'otpauth://totp/AguaVigia:ana@example.com?secret=JBSWY3DPEHPK3PXP&issuer=AguaVigia'

  it('debeDibujarUnCuadroPorCadaModuloOscuroConMargenDeCuatro', () => {
    const { modules } = create(uri, { errorCorrectionLevel: 'M' })
    const oscuros = Array.from(modules.data).filter(Boolean).length
    const { lado, trazo } = trazoQr(uri)
    expect(lado).toBe(modules.size + 8)
    expect(trazo.match(/M/g)).toHaveLength(oscuros)
    expect(trazo.startsWith('M4 4')).toBe(true)
  })

  it('debeAgruparElSecretoDeCuatroEnCuatro', () => {
    expect(agruparSecreto('JBSWY3DPEHPK3PXP')).toBe('JBSW Y3DP EHPK 3PXP')
    expect(agruparSecreto('JBSW Y3DP EH')).toBe('JBSW Y3DP EH')
  })
})
