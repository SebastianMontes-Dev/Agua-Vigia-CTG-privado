import { describe, expect, it } from 'vitest'
import { normalizarError } from './cliente'
import { veredictoDe } from './cuentas'

const fallo = (estado: number, cuerpo: unknown = null) => ({ ok: false as const, error: normalizarError(new Response(null, { status: estado }), cuerpo) })

describe('veredictoDe', () => {
  it('debeDarPorHechoUnaRespuestaCorrecta', () => {
    expect(veredictoDe({ ok: true, datos: undefined })).toEqual({ tipo: 'listo' })
  })

  it.each([[404], [409], [401]])('debeTratarUn%iComoUnEnlaceQueNoSirve', (estado) => {
    expect(veredictoDe(fallo(estado))).toEqual({ tipo: 'invalido', detalle: null })
  })

  it('debeMostrarElDetalleSoloEnUn400', () => {
    const cuerpo = { type: 'https://aguavigia.example/errores/peticion-invalida', detail: 'La clave repite demasiado los mismos caracteres' }
    expect(veredictoDe(fallo(400, cuerpo))).toEqual({ tipo: 'invalido', detalle: 'La clave repite demasiado los mismos caracteres' })
  })

  it('debeDistinguirLimiteFalloDeRedYErrorDelServidor', () => {
    expect(veredictoDe({ ok: false, error: normalizarError(null, null) })).toEqual({ tipo: 'sin-red' })
    expect(veredictoDe(fallo(429)).tipo).toBe('esperar')
    expect(veredictoDe(fallo(503)).tipo).toBe('fallo')
  })
})
