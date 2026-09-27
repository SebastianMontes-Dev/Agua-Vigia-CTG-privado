import { expect, test } from '@playwright/test'
import { problema, simularApi } from './api-simulada'

// Dentro del cuadro de Armenia en la geometría simulada.
const EN_ARMENIA = { latitude: 10.408, longitude: -75.502 }

test('debeAbrirElBarrioDeLaUbicacionYEnviarlaConElReporte', async ({ page, context }) => {
  const { reportes } = await simularApi(page)
  await context.grantPermissions(['geolocation'])
  await context.setGeolocation({ ...EN_ARMENIA, accuracy: 25 })
  await page.goto('/')

  await page.getByRole('button', { name: 'Usar mi ubicación' }).click()
  await expect(page).toHaveURL(/\/sectores\/armenia$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Armenia' })).toBeVisible()

  await page.getByRole('button', { name: 'Reportar lo que pasa en mi casa' }).click()
  await expect(page.getByRole('dialog')).toContainText('con la ubicación que compartiste')
  await page.getByRole('button', { name: 'No tengo agua' }).click()
  await expect(page.getByRole('heading', { name: 'Reporte recibido' })).toBeVisible()
  expect(reportes[0]).toMatchObject({ sectorId: 'armenia', coordenada: { latitud: EN_ARMENIA.latitude, longitud: EN_ARMENIA.longitude } })
})

test('noDebeEnviarLaUbicacionConElReporteDeOtroBarrio', async ({ page, context }) => {
  const { reportes } = await simularApi(page)
  await context.grantPermissions(['geolocation'])
  await context.setGeolocation({ ...EN_ARMENIA, accuracy: 25 })
  await page.goto('/')
  await page.getByRole('button', { name: 'Usar mi ubicación' }).click()
  await expect(page).toHaveURL(/\/sectores\/armenia$/)

  await page.getByRole('combobox', { name: 'Busca tu barrio' }).fill('manga')
  await page.getByRole('option', { name: /Manga/ }).click()
  await page.getByRole('button', { name: 'Reportar lo que pasa en mi casa' }).click()
  await page.getByRole('button', { name: 'Llega poca agua' }).click()
  await expect(page.getByRole('heading', { name: 'Reporte recibido' })).toBeVisible()
  expect(reportes[0]).toMatchObject({ sectorId: 'manga' })
  expect(reportes[0]).not.toHaveProperty('coordenada')
})

test('debeVolverALaBusquedaSiNoHayPermisoDeUbicacion', async ({ page }) => {
  await page.addInitScript(() => {
    navigator.geolocation.getCurrentPosition = (_exito, fallo) =>
      fallo?.({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3, message: '' } as GeolocationPositionError)
  })
  await simularApi(page)
  await page.goto('/')

  await page.getByRole('button', { name: 'Usar mi ubicación' }).click()
  await expect(page.getByRole('alert')).toContainText('No tenemos permiso para usar tu ubicación')
  await expect(page).toHaveURL(/\/$/)
  await page.getByRole('button', { name: 'Buscar mi barrio por su nombre' }).click()
  await expect(page.getByRole('combobox', { name: 'Busca tu barrio' })).toBeFocused()
})

test('debeDecirCuandoLaUbicacionQuedaFueraDeLosBarrios', async ({ page, context }) => {
  await simularApi(page)
  await context.grantPermissions(['geolocation'])
  await context.setGeolocation({ latitude: 4.6, longitude: -74.08, accuracy: 25 })
  await page.goto('/')
  await page.getByRole('button', { name: 'Usar mi ubicación' }).click()
  await expect(page.getByRole('alert')).toContainText('no queda dentro de ninguno de los barrios')
})

test('debeConfirmarUnReporteSoloAlTocarElBoton', async ({ page }) => {
  const { confirmaciones } = await simularApi(page)
  await page.goto('/confirmar/r9')

  await expect(page.getByRole('heading', { level: 1, name: 'Confirmar reporte' })).toBeVisible()
  await page.waitForTimeout(500)
  expect(confirmaciones).toHaveLength(0)

  await page.getByRole('button', { name: 'Confirmar este reporte' }).click()
  await expect(page.getByRole('heading', { name: 'Confirmación recibida' })).toBeVisible()
  await expect(page.getByText(/El reporte dice que en Armenia no llega agua/)).toBeVisible()
  await expect(page.getByText('Lo confirman 2 vecinos además de quien lo envió.')).toBeVisible()
  expect(confirmaciones).toEqual([{ id: 'r9', cuerpo: { huella: expect.stringMatching(/^[0-9a-f]{64}$/) } }])

  await page.getByRole('link', { name: 'Ver Armenia en el mapa' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Armenia' })).toBeVisible()
})

test('debeDecirQueElReporteNoEstaDisponibleAnteUn404', async ({ page }) => {
  await simularApi(page, {
    confirmacion: (route) => route.fulfill(problema(404, 'recurso-no-encontrado', "No existe el reporte 'x'")),
  })
  await page.goto('/confirmar/x')
  await page.getByRole('button', { name: 'Confirmar este reporte' }).click()
  await expect(page.getByRole('alert')).toContainText('Este reporte no está disponible')
  await expect(page.getByRole('button', { name: 'Confirmar este reporte' })).toHaveCount(0)
})

test('debeOfrecerElEnlaceDeConfirmacionDespuesDeReportar', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  // Sin la hoja de compartir del sistema, el enlace se copia.
  await page.addInitScript(() => { Object.defineProperty(navigator, 'share', { value: undefined }) })
  await simularApi(page)
  await page.goto('/sectores/armenia')
  await page.getByRole('button', { name: 'Reportar lo que pasa en mi casa' }).click()
  await page.getByRole('button', { name: 'No tengo agua' }).click()

  await page.getByRole('button', { name: 'Pedir a un vecino que lo confirme' }).click()
  await expect(page.getByText('Enlace copiado')).toBeVisible()
  const copiado = await page.evaluate(() => navigator.clipboard.readText())
  expect(copiado).toMatch(/^En Armenia no llega agua a mi casa\..* http:\/\/localhost:\d+\/confirmar\/r1$/)
})

test('noDebeOfrecerConfirmarElReportePropio', async ({ page }) => {
  const { confirmaciones } = await simularApi(page)
  await page.goto('/sectores/armenia')
  await page.getByRole('button', { name: 'Reportar lo que pasa en mi casa' }).click()
  await page.getByRole('button', { name: 'No tengo agua' }).click()
  await expect(page.getByRole('heading', { name: 'Reporte recibido' })).toBeVisible()

  await page.goto('/confirmar/r1')
  await expect(page.getByText('Este reporte lo enviaste tú')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Confirmar este reporte' })).toHaveCount(0)
  expect(confirmaciones).toHaveLength(0)
})

test('debeDecirQueUnaSegundaConfirmacionNoSeCuentaDosVeces', async ({ page }) => {
  await simularApi(page)
  await page.goto('/confirmar/r9')
  await page.getByRole('button', { name: 'Confirmar este reporte' }).click()
  await expect(page.getByRole('heading', { name: 'Confirmación recibida' })).toBeVisible()

  await page.reload()
  await page.getByRole('button', { name: 'Confirmar este reporte' }).click()
  await expect(page.getByRole('heading', { name: 'Ya habías confirmado este reporte' })).toBeVisible()
})
