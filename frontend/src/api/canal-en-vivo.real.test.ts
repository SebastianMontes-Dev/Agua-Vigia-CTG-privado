import { it, expect } from 'vitest'
import { crearCanal } from './canal-en-vivo'

it.skipIf(!process.env.AGUAVIGIA_PRUEBA_REAL)('recibe el listado por el canal contra el backend real', async () => {
  const http: typeof fetch = (ruta, opciones) => fetch(`http://localhost:5173${String(ruta)}`, opciones)
  const canal = crearCanal({ fetch: http, conectar: http, ahora: Date.now, aleatorio: () => 0.5,
    programar: (accion, ms) => setTimeout(accion, ms), cancelar: clearTimeout,
    visible: () => true, enLinea: () => true, observar: () => () => {}, recuperar: () => null, guardar: () => {} })
  canal.iniciar()
  try {
    await new Promise((resolve) => setTimeout(resolve, 3000))
    expect(canal.lectura().estado).toBe('en-vivo')
    expect(canal.lectura().listado?.sectores).toHaveLength(211)
    expect(canal.lectura().listado?.generadoEn).toBeTruthy()
  } finally { canal.detener() }
}, 10000)
