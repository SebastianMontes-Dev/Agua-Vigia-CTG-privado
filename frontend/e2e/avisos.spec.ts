import { expect, test } from '@playwright/test'
import { problema, simularApi } from './api-simulada'

test('debeElegirVariosBarriosYMostrarSiempreLaRespuestaNeutra', async ({ page }) => {
  await simularApi(page)
  const enviados: { correo: string; sectorIds: string[] }[] = []
  await page.route('**/api/suscripciones', (ruta) => {
    enviados.push(ruta.request().postDataJSON())
    return ruta.fulfill({ status: 201, contentType: 'application/json', body: '{}' })
  })
  await page.goto('/avisos?sector=manga')
  await expect(page.getByRole('list', { name: 'Barrios elegidos' })).toContainText('Manga')
  await page.getByRole('textbox', { name: 'Correo electrónico' }).fill('vecina@example.com')
  await page.getByRole('combobox', { name: 'Busca un barrio para recibir avisos' }).fill('Armenia')
  await page.getByRole('option', { name: 'Armenia' }).click()
  await page.getByRole('button', { name: /Enviar enlace de confirmación/ }).click()
  await expect(page.getByRole('status')).toContainText('Si la dirección es válida, te enviamos un correo. El enlace vence en 48 horas')
  expect(enviados).toEqual([{ correo: 'vecina@example.com', sectorIds: ['manga', 'armenia'] }])
})

test('debeMostrarValidacionLimiteYFalloDeRedSinReintentoAutomatico', async ({ page }) => {
  await simularApi(page)
  let intentos = 0
  await page.route('**/api/suscripciones', (ruta) => {
    intentos++
    if (intentos === 1) return ruta.fulfill(problema(400, 'peticion-invalida', 'Correo inválido'))
    if (intentos === 2) return ruta.fulfill({ ...problema(429, 'limite-de-peticiones-excedido', 'Límite'), headers: { 'Retry-After': '30' } })
    return ruta.abort()
  })
  await page.goto('/avisos')
  await page.getByRole('textbox', { name: 'Correo electrónico' }).fill('vecina@example.com')
  await page.getByRole('combobox', { name: 'Busca un barrio para recibir avisos' }).fill('Manga')
  await page.getByRole('option', { name: 'Manga' }).click()
  const boton = page.getByRole('button', { name: /Enviar enlace de confirmación/ })
  await boton.click()
  await expect(page.getByRole('status')).toContainText('Revisa el correo y los barrios')
  await boton.click()
  await expect(page.getByRole('status')).toContainText('30 segundos')
  await boton.click()
  await expect(page.getByRole('status')).toContainText('Revisa tu conexión')
  expect(intentos).toBe(3)
})

test('debePedirUnBotonAntesDeConfirmarOBajarYRetirarElTokenDeLaURL', async ({ page }) => {
  await simularApi(page)
  const acciones: string[] = []
  await page.route(/\/api\/suscripciones\/(confirmar|cancelar)\?token=/, (ruta) => {
    acciones.push(ruta.request().url())
    expect(ruta.request().headers().accept).toContain('application/json')
    return ruta.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })
  await page.goto('/avisos/confirmar?token=secreto')
  await expect(page).toHaveURL(/\/avisos\/confirmar$/)
  expect(acciones).toHaveLength(0)
  await expect(page.locator('meta[name="referrer"]')).toHaveAttribute('content', 'no-referrer')
  await page.getByRole('button', { name: 'Confirmar avisos' }).click()
  await expect(page.getByRole('status')).toContainText('Avisos confirmados')
  await page.goto('/avisos/baja?token=secreto')
  await expect(page).toHaveURL(/\/avisos\/baja$/)
  expect(acciones).toHaveLength(1)
  await page.getByRole('button', { name: 'Dejar de recibir avisos' }).click()
  await expect(page.getByRole('status')).toContainText('Ya no recibirás estos avisos')
  expect(acciones).toHaveLength(2)
})

test('debeExplicarTokenAusenteOInvalido', async ({ page }) => {
  await simularApi(page)
  await page.goto('/avisos/confirmar')
  await expect(page.getByRole('alert')).toContainText('Falta el enlace completo')
  await page.route('**/api/suscripciones/confirmar?token=*', (ruta) => ruta.fulfill(problema(400, 'peticion-invalida', 'Venció')))
  await page.goto('/avisos/confirmar?token=vencido')
  await page.getByRole('button', { name: 'Confirmar avisos' }).click()
  await expect(page.getByRole('alert')).toContainText('no es válido o venció')
})

test('debeResponderEnLaPrimeraVistaYMedirMenosDeDosPantallasAMovil', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await simularApi(page)
  await page.goto('/avisos')
  await expect(page.getByRole('button', { name: /Enviar enlace de confirmación/ })).toBeInViewport()
  const alto = await page.locator('main').evaluate((nodo) => nodo.scrollHeight)
  expect(alto).toBeLessThanOrEqual(1688)
})
