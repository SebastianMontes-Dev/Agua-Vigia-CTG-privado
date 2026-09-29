import { chromium } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'

const salida = new URL('../../docs/gestion/evidencias/f4-avisos-5-segundos/', import.meta.url)
const tamanos = [[1440, 900], [1024, 768], [768, 1024], [390, 844]]
const rutas = [
  ['avisos', '/avisos?sector=manga', 'Enviar enlace de confirmación'],
  ['confirmar', '/avisos/confirmar?token=ejemplo', 'Confirmar avisos'],
  ['baja', '/avisos/baja?token=ejemplo', 'Dejar de recibir avisos'],
]
const sectores = { generadoEn: '2026-09-28T12:00:00Z', sectores: [
  { id: 'armenia', nombre: 'ARMENIA' }, { id: 'manga', nombre: 'MANGA' }, { id: 'el-bosque', nombre: 'EL BOSQUE' },
] }
const navegador = await chromium.launch()
const medidas = []
await mkdir(salida, { recursive: true })
for (const [width, height] of tamanos) for (const tema of ['claro', 'oscuro']) {
  const contexto = await navegador.newContext({ viewport: { width, height } })
  await contexto.addInitScript((valor) => localStorage.setItem('aguavigia.tema', valor), tema)
  const pagina = await contexto.newPage()
  await pagina.route('**/api/sectores', (ruta) => ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(sectores) }))
  for (const [nombre, ruta, textoBoton] of rutas) {
    await pagina.goto('http://127.0.0.1:4173' + ruta)
    if (nombre === 'avisos') {
      await pagina.getByRole('textbox', { name: 'Correo electrónico' }).fill('vecina@example.com')
      await pagina.getByRole('combobox', { name: 'Busca un barrio para recibir avisos' }).fill('Armenia')
      await pagina.getByRole('option', { name: 'Armenia' }).click()
    }
    await pagina.evaluate(() => document.fonts.ready)
    await pagina.waitForTimeout(350)
    const boton = await pagina.getByRole('button', { name: textoBoton }).boundingBox()
    const alto = await pagina.evaluate(() => document.documentElement.scrollHeight)
    await pagina.screenshot({ path: new URL(`${nombre}-${width}x${height}-${tema}.png`, salida).pathname, fullPage: true })
    medidas.push({ ruta: nombre, tamano: `${width}x${height}`, tema, alto, botonInferior: Math.round(boton.y + boton.height) })
  }
  await contexto.close()
}
const contexto = await navegador.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' })
await contexto.addInitScript(() => localStorage.setItem('aguavigia.tema', 'claro'))
const pagina = await contexto.newPage()
await pagina.route('**/api/sectores', (ruta) => ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(sectores) }))
await pagina.goto('http://127.0.0.1:4173/avisos?sector=manga')
await pagina.getByRole('textbox', { name: 'Correo electrónico' }).fill('vecina@example.com')
await pagina.screenshot({ path: new URL('avisos-390x844-claro-movimiento-reducido.png', salida).pathname, fullPage: true })
await contexto.close()
await navegador.close()
await writeFile(new URL('medidas.json', salida), JSON.stringify(medidas, null, 2) + '\n')
