import { describe, expect, it } from 'vitest'
import { interpretarAlta, interpretarEnlace, MENSAJE_NEUTRO } from './avisos'

describe('avisos por correo', () => {
  it('conserva el mensaje neutro de la solicitud', () => {
    expect(MENSAJE_NEUTRO).toBe('Si la dirección es válida, te enviamos un correo. El enlace vence en 48 horas')
  })

  it.each([
    [201, 'recibido'], [400, 'invalido'], [500, 'fallo'],
  ])('interpreta el estado %i del alta como %s', (estado, tipo) => {
    expect(interpretarAlta(new Response(null, { status: estado })).tipo).toBe(tipo)
  })

  it('lee Retry-After sin reintentar el alta', () => {
    expect(interpretarAlta(new Response(null, { status: 429, headers: { 'Retry-After': '27' } })))
      .toEqual({ tipo: 'esperar', segundos: 27 })
  })

  it('distingue un fallo de red del alta', () => {
    expect(interpretarAlta(null)).toEqual({ tipo: 'sin-red' })
  })

  it.each([
    [200, 'completado'], [400, 'invalido'], [409, 'invalido'], [503, 'fallo'],
  ])('interpreta el estado %i del enlace como %s', (estado, tipo) => {
    expect(interpretarEnlace(new Response(null, { status: estado })).tipo).toBe(tipo)
  })

  it('lee Retry-After en el enlace', () => {
    expect(interpretarEnlace(new Response(null, { status: 429, headers: { 'Retry-After': '12' } })))
      .toEqual({ tipo: 'esperar', segundos: 12 })
  })

  it('distingue un fallo de red del enlace', () => {
    expect(interpretarEnlace(null)).toEqual({ tipo: 'sin-red' })
  })
})
