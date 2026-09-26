import { expect, test } from '@playwright/test'
import { AxeBuilder } from '@axe-core/playwright'
test('consulta mapa local, barrio y registro sin recursos externos', async ({ page }) => {
  const externos: string[] = []
  page.on('request', (peticion) => { if (peticion.url().startsWith('http') && !peticion.url().startsWith('http://localhost:4173')) externos.push(peticion.url()) })
  await page.goto('/')
  await expect(page.locator('.lista-barrios li')).toHaveCount(211)
  await expect(page.locator('[data-estados-listos="true"]')).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText('En vivo', { exact: true })).toBeVisible()
  await page.getByLabel('Busca tu barrio').fill('zona industrial')
  await page.locator('.lista-barrios a').click()
  await expect(page.locator('.ficha h1')).toContainText('ZONA INDUSTRIAL')
  await expect(page.locator('.registro')).toContainText('Última verificación')
  expect(externos).toEqual([])
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})
test('reporta en dos toques y el cuarto recibe el límite por dispositivo', async ({ page }) => {
  await page.goto('/sectores/bocagrande')
  await expect(page.locator('.ficha h1')).toContainText('BOCAGRANDE')
  for (let intento = 0; intento < 4; intento++) {
    await page.getByRole('button', { name: 'Reportar en este barrio' }).focus()
    await page.keyboard.press('Enter')
    await page.getByRole('button', { name: 'No hay agua', exact: true }).click()
    const dialogo = page.getByRole('dialog')
    if (intento < 3) {
      await expect(dialogo).toContainText('Gracias. Tu reporte cuenta junto con el de tus vecinos')
      await page.keyboard.press('Escape')
      await expect(page.getByRole('button', { name: 'Reportar en este barrio' })).toBeFocused()
    } else await expect(dialogo.getByRole('alert')).toContainText('Ya recibimos tres reportes tuyos')
  }
})
test('el consenso de tres huellas llega por SSE y refresca el listado', async ({ page, request }) => {
  const sectorId = 'arroyo-grande'
  await page.goto('/')
  await expect(page.getByText('En vivo', { exact: true })).toBeVisible()
  await page.getByLabel('Busca tu barrio').fill('arroyo grande')
  await expect(page.locator('.lista-barrios a')).toHaveCount(1)
  const actual = await page.locator('.lista-barrios .estado').innerText()
  const tipo = actual === 'Sin servicio' ? 'SERVICIO_RESTABLECIDO' : 'SIN_AGUA'
  const esperado = tipo === 'SIN_AGUA' ? 'Sin servicio' : 'Con servicio'
  for (let vecino = 0; vecino < 3; vecino++) {
    const huella = crypto.randomUUID().replaceAll('-', '') + crypto.randomUUID().replaceAll('-', '')
    const respuesta = await request.post('/api/reportes', { data: { sectorId, tipo, huella } })
    expect(respuesta.status()).toBe(201)
  }
  await expect(page.locator('.lista-barrios .estado')).toHaveText(esperado, { timeout: 30_000 })
})
test('abrir confirmación no actúa; confirmar sí y el inexistente informa 404', async ({ page, request }) => {
  const respuesta = await request.post('/api/reportes', { data: { sectorId: 'manga', tipo: 'PRESION_BAJA', huella: crypto.randomUUID().repeat(2) } })
  expect(respuesta.status()).toBe(201)
  const reporte = await respuesta.json() as { id: string }
  let peticiones = 0
  page.on('request', (peticion) => { if (peticion.method() === 'POST') peticiones++ })
  await page.goto(`/confirmar/${reporte.id}`)
  expect(peticiones).toBe(0)
  await page.getByRole('button', { name: 'Confirmar reporte', exact: true }).click()
  await expect(page.getByText('Gracias. Tu confirmación quedó registrada.')).toBeVisible()
  expect(peticiones).toBe(1)
  await page.goto('/confirmar/no-existe')
  await page.getByRole('button', { name: 'Confirmar reporte', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('no está disponible')
})
test('ofrece foto solo después del 201, rechaza 10 MiB y conserva el reporte', async ({ page }) => {
  await page.goto('/sectores/centro')
  await page.getByRole('button', { name: 'Reportar en este barrio' }).click()
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  await page.getByRole('button', { name: 'Ya volvió el agua', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('Gracias. Tu reporte cuenta junto con el de tus vecinos')
  await page.locator('input[type=file]').setInputFiles({ name: 'grande.png', mimeType: 'image/png', buffer: Buffer.alloc(10 * 1024 * 1024) })
  await expect(page.getByRole('alert')).toContainText('Tu reporte sigue guardado')
  const respuesta = page.waitForResponse((valor) => valor.url().endsWith('/foto') && valor.request().method() === 'POST')
  await page.locator('input[type=file]').setInputFiles({ name: 'prueba.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j5nQAAAAASUVORK5CYII=', 'base64') })
  expect((await respuesta).status()).toBe(200)
  await expect(page.getByText('Foto adjunta', { exact: false })).toBeVisible()
})
test('ubicación fuera de Cartagena permite elegir el barrio a mano sin repetir POST', async ({ page, context }) => {
  await context.grantPermissions(['geolocation'])
  await context.setGeolocation({ longitude: 0, latitude: 0 })
  await page.goto('/')
  await page.getByRole('button', { name: 'Reportar', exact: true }).click()
  await page.getByRole('button', { name: 'Usar mi ubicación (opcional)' }).click()
  await expect(page.getByText('Ubicación obtenida', { exact: true })).toBeVisible()
  let envios = 0
  page.on('request', (peticion) => { if (peticion.method() === 'POST') envios++ })
  await page.getByRole('button', { name: 'No hay agua', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Elige el barrio a mano')
  expect(envios).toBe(1)
  await page.getByLabel('Barrio', { exact: true }).selectOption('centro')
  await page.getByRole('button', { name: 'No hay agua', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('Gracias. Tu reporte cuenta junto con el de tus vecinos')
  expect(envios).toBe(2)
})
