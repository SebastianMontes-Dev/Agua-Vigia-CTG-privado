import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'

const informar = (texto) => process.stdout.write(texto + '\n')
const origen = process.env.PROXY_URL ?? 'http://localhost:5173'
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
async function pedir(ruta, opciones, esperado = 200) {
  const respuesta = await fetch(origen + ruta, opciones)
  if (respuesta.status !== esperado) assert.fail(`${ruta}: ${respuesta.status} ${await respuesta.text()}`)
  return respuesta
}
const listado = await (await pedir('/api/sectores')).json()
assert.equal(listado.sectores.length, 211); assert.ok(listado.generadoEn)
informar(`${origen} GET sectores: 200, 211 sectores, generadoEn=${listado.generadoEn}`)
const geometria = await pedir('/api/sectores/geometria')
assert.equal((await geometria.json()).features.length, 211)
informar('GET geometria sin Accept JSON: 200, 211 polígonos')
const control = new AbortController()
const stream = await pedir('/api/sectores/stream', { signal: control.signal, headers: { Accept: 'text/event-stream' } })
assert.match(stream.headers.get('Content-Type'), /text\/event-stream/)
let avisos = 0, texto = ''
const lector = stream.body.getReader(), decodificador = new TextDecoder()
const lectura = (async () => {
  try {
    while (true) {
      const { done, value } = await lector.read(); if (done) break
      texto += decodificador.decode(value, { stream: true })
      avisos = (texto.match(/event:sectores/g) ?? []).length
    }
  } catch (error) { if (!control.signal.aborted) throw error }
})()
try {
  const sector = listado.sectores.find((s) => s.id === (process.env.SECTOR_PRUEBA ?? 'alto-bosque') && (s.poblacion ?? 0) <= 3000)
  assert.ok(sector)
  const tipo = sector.estado === 'SIN_SERVICIO' ? 'SERVICIO_RESTABLECIDO' : 'SIN_AGUA'
  let primerReporte
  const inicio = Date.now()
  for (let i = 0; i < 3; i++) {
    const respuesta = await pedir('/api/reportes', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sectorId: sector.id, tipo, huella: randomBytes(32).toString('hex') }) }, 201)
    primerReporte ??= await respuesta.json()
  }
  for (let i = 0; i < 40; i++) { if (avisos >= 2) break; await new Promise((resolve) => setTimeout(resolve, 250)) }
  assert.ok(avisos >= 2, `no llegó el aviso de consenso: ${texto}`)
  informar(`SSE: ${avisos} avisos sin búfer; consenso en ${Date.now() - inicio} ms, sector ${sector.id}`)
  const formulario = new FormData(); formulario.append('foto', new Blob([png], { type: 'image/png' }), 'prueba.png')
  const foto = await (await pedir(`/api/reportes/${primerReporte.id}/foto`, { method: 'POST', body: formulario })).json()
  const archivo = await pedir(foto.fotoUrl)
  assert.match(archivo.headers.get('Content-Type'), /image\//)
  informar(`POST reporte: 201; POST foto: 200; GET ${foto.fotoUrl}: 200`)
  const eventos = await (await pedir('/api/bitacora?tamano=200')).json()
  const portada = eventos.find((e) => e.imagenUrl?.startsWith('https://www.acuacar.com/wp-content/uploads/'))
  assert.ok(portada, 'no hay portada real en la bitácora; esta comprobación queda pendiente')
  const ruta = portada.imagenUrl.replace('https://www.acuacar.com/wp-content/uploads/', '/acuacar-media/')
  const imagen = await pedir(ruta); assert.match(imagen.headers.get('Content-Type'), /image\//)
  informar(`GET ${ruta}: 200 ${imagen.headers.get('Content-Type')}`)
} finally { control.abort(); await lectura }
