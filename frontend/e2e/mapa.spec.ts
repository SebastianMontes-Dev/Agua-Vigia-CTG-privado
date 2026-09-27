import { expect, test } from '@playwright/test'
import { problema, simularApi } from './api-simulada'

test('debeResponderElEstadoYElFinPrometidoDeUnBarrioBuscado', async ({ page }) => {
  await simularApi(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1, name: 'Cartagena ahora' })).toBeVisible()

  await page.getByRole('combobox', { name: 'Busca tu barrio' }).fill('arme')
  await page.getByRole('option', { name: /Armenia/ }).click()

  await expect(page).toHaveURL(/\/sectores\/armenia$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Armenia' })).toBeVisible()
  await expect(page.getByText('Sin servicio', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('Fin prometido')).toBeVisible()
  await expect(page.getByText('Aviso oficial de Acuacar.')).toBeVisible()
})

test('debePresentarElEstadoNuloComoSinDatosYNuncaComoConServicio', async ({ page }) => {
  await simularApi(page)
  await page.goto('/sectores/manga')
  const ficha = page.getByRole('article', { name: 'Manga' })
  await expect(ficha.getByText('Sin datos verificados')).toBeVisible()
  await expect(ficha.getByText('Con servicio')).toHaveCount(0)
  await expect(ficha.getByText('Barrio · sin dato censal')).toBeVisible()
  await expect(ficha.getByText('Sin fecha de registro.')).toBeVisible()
})

test('debeAdvertirSinVerificacionRecienteSinCambiarElEstado', async ({ page }) => {
  await simularApi(page)
  await page.goto('/sectores/el-bosque')
  const ficha = page.getByRole('article', { name: 'El Bosque' })
  await expect(ficha.getByText('Con servicio')).toBeVisible()
  await expect(ficha.getByText(/Sin verificación reciente/)).toBeVisible()
})

test('debeReportarEnDosToquesConLaHuellaDelDispositivo', async ({ page }) => {
  const { reportes } = await simularApi(page)
  await page.goto('/sectores/armenia')

  await page.getByRole('button', { name: 'Reportar lo que pasa en mi casa' }).click()
  await page.getByRole('button', { name: 'No tengo agua' }).click()

  const dialogo = page.getByRole('dialog')
  await expect(dialogo.getByRole('heading', { name: 'Reporte recibido' })).toBeVisible()
  await expect(dialogo.getByText(/cuenta junto con los de tus vecinos/)).toBeVisible()
  expect(reportes).toHaveLength(1)
  expect(reportes[0]).toMatchObject({ tipo: 'SIN_AGUA', sectorId: 'armenia', huella: expect.stringMatching(/^[0-9a-f]{64}$/) })
})

test('debeExplicarElCupoAgotadoSinReintentarSolo', async ({ page }) => {
  const { reportes } = await simularApi(page, {
    reporte: (route) => route.fulfill(problema(429, 'limite-reportes-excedido', 'Límite de reportes')),
  })
  await page.goto('/sectores/armenia')
  await page.getByRole('button', { name: 'Reportar lo que pasa en mi casa' }).click()
  await page.getByRole('button', { name: 'Llega poca agua' }).click()

  await expect(page.getByRole('alert')).toContainText('Ya enviaste tres reportes de este barrio')
  await page.waitForTimeout(1500)
  expect(reportes).toHaveLength(1)
})

test('debeAvisarSinCifrasCuandoNoSePuedeConsultarElEstado', async ({ page }) => {
  await simularApi(page, {
    sectores: (route) => route.fulfill(problema(503, 'base-de-datos-no-disponible', 'Base de datos no disponible')),
  })
  await page.goto('/')
  await expect(page.getByRole('alert')).toContainText('No pudimos consultar el estado')
  await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible()
  await expect(page.getByText('Con servicio')).toHaveCount(0)
})

test('debeOfrecerLaListaDeBarriosComoAlternativaAlMapa', async ({ page }) => {
  await simularApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Ver la lista de los 3 barrios con su estado' }).click()
  const lista = page.getByRole('region', { name: 'Todos los barrios' })
  await expect(lista.getByRole('link')).toHaveCount(3)
  await lista.getByRole('link', { name: /Manga/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Manga' })).toBeVisible()
})

test('noDebeDesbordarLaPaginaHaciaLosLados', async ({ page }) => {
  await simularApi(page)
  await page.goto('/sectores/armenia')
  await expect(page.getByRole('heading', { level: 1, name: 'Armenia' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
