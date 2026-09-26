import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { crearCanal, parserSse, CIERRE_PESTANA_OCULTA_MS, type EntornoCanal } from './canal-en-vivo'

function preparar() {
  let visible = true, enLinea = true, observar = () => {}, emitir = (_texto: string) => {}, cerrar = () => {}
  const listado = { sectores: [{ id: 'manga', estado: null }], generadoEn: '2026-09-26T00:00:00Z' }
  const fetch = vi.fn<typeof globalThis.fetch>(async () => Response.json(listado))
  const conectar = vi.fn<typeof globalThis.fetch>(async () => new Response(new ReadableStream({ start(control) {
    emitir = (texto) => control.enqueue(new TextEncoder().encode(texto)); cerrar = () => control.close()
  } }), { headers: { 'Content-Type': 'text/event-stream' } }))
  const entorno: EntornoCanal = {
    fetch, conectar, ahora: Date.now, aleatorio: () => 0.5,
    programar: (accion, ms) => setTimeout(accion, ms), cancelar: clearTimeout,
    visible: () => visible, enLinea: () => enLinea, observar: (accion) => { observar = accion; return () => {} },
    recuperar: () => null, guardar: vi.fn<EntornoCanal['guardar']>(),
  }
  const canal = crearCanal(entorno)
  return { canal, entorno, fetch, conectar, listado, emitir: (texto: string) => emitir(texto), cerrar: () => cerrar(),
    ocultar: () => { visible = false; observar() }, mostrar: () => { visible = true; observar() },
    desconectar: () => { enLinea = false; observar() }, reconectar: () => { enLinea = true; observar() } }
}

beforeEach(() => { vi.useFakeTimers(); vi.setSystemTime(0) })
afterEach(() => { vi.useRealTimers() })
describe('canal en vivo', () => {
  it('diez avisos seguidos no hacen más de un GET cada cinco segundos', async () => {
    const p = preparar(); p.canal.iniciar(); await vi.advanceTimersByTimeAsync(0)
    for (let i = 0; i < 10; i++) p.emitir('event: sectores\ndata: {}\n\n')
    await vi.advanceTimersByTimeAsync(4999); expect(p.fetch).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1); expect(p.fetch).toHaveBeenCalledTimes(2); p.canal.detener()
  })
  it('espera el jitter de cero a tres segundos y comparte límite con reintento manual', async () => {
    const p = preparar(); p.canal.iniciar(); await vi.advanceTimersByTimeAsync(6000)
    p.emitir('event: sectores\ndata: {}\n\n')
    await vi.advanceTimersByTimeAsync(1499); expect(p.fetch).toHaveBeenCalledTimes(1)
    p.canal.actualizar(); await vi.advanceTimersByTimeAsync(1); expect(p.fetch).toHaveBeenCalledTimes(2); p.canal.detener()
  })
  it('reconecta al cierre normal respetando retry y sin mostrar error', async () => {
    const p = preparar(); p.canal.iniciar(); await vi.advanceTimersByTimeAsync(0)
    p.emitir('retry: 7000\n\n'); await vi.advanceTimersByTimeAsync(0); p.cerrar()
    await vi.advanceTimersByTimeAsync(6999); expect(p.conectar).toHaveBeenCalledTimes(1)
    expect(p.canal.lectura().error).toBe(false)
    await vi.advanceTimersByTimeAsync(1); expect(p.conectar).toHaveBeenCalledTimes(2); p.canal.detener()
  })
  it('acota a sesenta segundos el backoff después del jitter', async () => {
    const p = preparar(); p.entorno.aleatorio = () => 1
    p.conectar.mockRejectedValue(new Error('red')); p.canal.iniciar()
    await vi.advanceTimersByTimeAsync(0)
    for (const ms of [1500, 3000, 6000, 12000, 24000, 48000, 60000]) await vi.advanceTimersByTimeAsync(ms)
    const cuenta = p.conectar.mock.calls.length
    await vi.advanceTimersByTimeAsync(59999); expect(p.conectar).toHaveBeenCalledTimes(cuenta)
    await vi.advanceTimersByTimeAsync(1); expect(p.conectar).toHaveBeenCalledTimes(cuenta + 1); p.canal.detener()
  })
  it('ante 429 sondea cada treinta segundos y respeta Retry-After', async () => {
    const p = preparar(); p.conectar.mockResolvedValueOnce(new Response(null, { status: 429, headers: { 'Retry-After': '90' } }))
    p.canal.iniciar(); await vi.advanceTimersByTimeAsync(0); expect(p.canal.lectura().estado).toBe('sondeando')
    await vi.advanceTimersByTimeAsync(89999); expect(p.fetch).toHaveBeenCalledTimes(3); expect(p.conectar).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1); expect(p.conectar).toHaveBeenCalledTimes(2); p.canal.detener()
  })
  it('no pide nada oculta, cierra pasado un rato y refresca al regresar', async () => {
    const p = preparar(); p.canal.iniciar(); await vi.advanceTimersByTimeAsync(0)
    const signal = p.conectar.mock.calls[0]?.[1]?.signal
    p.ocultar(); p.emitir('event: sectores\ndata: {}\n\n'); await vi.advanceTimersByTimeAsync(CIERRE_PESTANA_OCULTA_MS + 60000)
    expect(p.fetch).toHaveBeenCalledTimes(1); expect(p.conectar).toHaveBeenCalledTimes(1); expect(signal?.aborted).toBe(true)
    p.mostrar(); await vi.advanceTimersByTimeAsync(0); expect(p.fetch).toHaveBeenCalledTimes(2); p.canal.detener()
  })
  it('conserva fecha y estado nulo sin red y vuelve a consultar al recuperar señal', async () => {
    const p = preparar(); p.canal.iniciar(); await vi.advanceTimersByTimeAsync(0)
    p.desconectar(); await vi.advanceTimersByTimeAsync(100000)
    expect(p.canal.lectura()).toMatchObject({ estado: 'sin-red', listado: p.listado }); expect(p.fetch).toHaveBeenCalledTimes(1)
    p.reconectar(); await vi.advanceTimersByTimeAsync(0); expect(p.fetch).toHaveBeenCalledTimes(2); p.canal.detener()
  })
  it('solo reintenta GET y como máximo tres veces, conservando el listado', async () => {
    const p = preparar(); p.canal.iniciar(); await vi.advanceTimersByTimeAsync(0)
    p.fetch.mockRejectedValue(new Error('red')); p.canal.actualizar(); await vi.advanceTimersByTimeAsync(25000)
    expect(p.fetch).toHaveBeenCalledTimes(5); expect(p.canal.lectura().listado).toEqual(p.listado); expect(p.canal.lectura().error).toBe(true)
    expect(p.fetch.mock.calls.every((args) => !args[1]?.method || args[1]?.method === 'GET')).toBe(true); p.canal.detener()
  })
})
describe('parser SSE', () => {
  it('tolera chunks parciales CRLF comentarios y datos de varias líneas', () => {
    const aviso = vi.fn<() => void>(), retry = vi.fn<(ms: number) => void>(), procesar = parserSse(aviso, retry)
    procesar(':latido\r'); procesar('\nretry: 9000\r\nevent: sect'); procesar('ores\r\ndata: uno\r\ndata: dos\r\n\r'); procesar('\n')
    expect(aviso).toHaveBeenCalledTimes(1); expect(retry).toHaveBeenCalledWith(9000)
    procesar('retry: -3\nevent: otro\ndata: {}\n\n'); expect(aviso).toHaveBeenCalledTimes(1); expect(retry).toHaveBeenCalledTimes(1)
  })
})
