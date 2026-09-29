import { expect, test, type Page } from '@playwright/test'
import { cumplimientoDecimalDeEjemplo, serieDeEjemplo } from '../src/pruebas/datos/historia'
import { problema, simularApi } from './api-simulada'

const serieLarga = Array.from({ length: 12 }, (_, indice) => ({
  ...serieDeEjemplo[indice % serieDeEjemplo.length]!,
  periodo: `2026-${String(indice + 1).padStart(2, '0')}`,
}))

function simularSerie(page: Page, serie: unknown) {
  return simularApi(page, { serie: (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(serie) }) })
}

test('debeTitularConElVeredictoYDarLasCifrasPorCorte', async ({ page }) => {
  await simularApi(page)
  await page.goto('/cumplimiento')
  // 9000 s de más entre los 28 cortes de la serie de ejemplo: unos 5 minutos por corte, sobre el umbral.
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Los cortes duran más de lo anunciado')
  await expect(page.getByText(/^Cumplimiento · 28 cortes cerrados · abril a septiembre de 2026$/)).toBeVisible()
  const cifras = page.getByRole('region', { name: 'Promedio por corte' })
  await expect(cifras.getByText('21 min', { exact: true })).toBeVisible()
  await expect(cifras.getByText('27 min', { exact: true })).toBeVisible()
  await expect(cifras.getByText('5 min más', { exact: true })).toBeVisible()
  await expect(cifras.getByText(/Índice 80\s?%/)).toBeVisible()
  await expect(page.getByRole('main')).toHaveCount(1)
})

test('debeMostrarElMesAMesSinRellenarMesesAusentes', async ({ page }) => {
  await simularSerie(page, [serieDeEjemplo[2], serieDeEjemplo[0]])
  await page.goto('/cumplimiento')
  const meses = page.locator('section[aria-labelledby="titulo-serie"] ol li')
  await expect(meses).toHaveCount(2)
  await expect(meses.first()).toContainText('abril de 2026')
  await expect(meses.first()).toContainText('30 min menos')
  await expect(meses.first()).toContainText('4 cortes')
  await expect(page.getByText('mayo de 2026')).toHaveCount(0)
})

test('debeMostrarAusenciaAnte400SinInventarPorcentaje', async ({ page }) => {
  await simularApi(page, { cumplimiento: (route) => route.fulfill(problema(400, 'peticion-invalida', 'No hay cortes cerrados')) })
  await page.goto('/cumplimiento')
  await expect(page.getByText(/Aún no hay cortes cerrados para medir/)).toBeVisible()
  await expect(page.getByText(/Índice/)).toHaveCount(0)
})

test('debeExplicarUnIndiceDecimalConComaColombiana', async ({ page }) => {
  await simularApi(page, { cumplimiento: (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(cumplimientoDecimalDeEjemplo),
  }) })
  await page.goto('/cumplimiento')
  await expect(page.getByText(/Índice 66,7\s?%/)).toBeVisible()
  await expect(page.getByText(/lo anunciado cubrió el 66,7\s?% de lo que duraron/)).toBeVisible()
  await expect(page.getByText('66.7%')).toHaveCount(0)
})

test('debeAplicarElBarrioAlIndiceALaSerieYAlCsv', async ({ page }) => {
  await simularApi(page)
  const consultas: URL[] = []
  page.on('request', (peticion) => {
    const url = new URL(peticion.url())
    if (url.pathname.startsWith('/api/cumplimiento')) consultas.push(url)
  })
  await page.goto('/cumplimiento')
  await page.getByLabel('Barrio').fill('manga')
  await page.getByRole('option', { name: 'Manga' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('En Manga, los cortes duran más de lo anunciado')
  await expect.poll(() => consultas.some((url) => url.pathname.endsWith('/sectores/manga'))).toBe(true)
  const serie = consultas.findLast((url) => url.pathname === '/api/cumplimiento/serie')
  expect(serie?.searchParams.get('sectorId')).toBe('manga')
  expect(serie?.searchParams.has('desde')).toBe(false)
  await page.getByText('Ver datos').click()
  await expect(page.getByRole('link', { name: 'Descargar en CSV' })).toHaveAttribute('href', '/api/cumplimiento/serie.csv?sectorId=manga')
})

test('debeDistinguirBarrioDesconocidoDeAusenciaDeCortes', async ({ page }) => {
  await simularApi(page, { cumplimiento: (route) => route.fulfill(problema(400, 'peticion-invalida', 'No hay cortes cerrados')) })
  await page.goto('/cumplimiento?sector=no-existe')
  await expect(page.getByText('No encontramos este barrio.')).toBeVisible()
  await expect(page.getByText(/Aún no hay cortes cerrados para medir/)).toHaveCount(0)
})

test('debeResponderEnLaPrimeraVistaYMostrarSeisMesesHastaPedirlos', async ({ page }) => {
  const celular = (page.viewportSize()?.width ?? 0) < 600
  const alto = celular ? 844 : 900
  await page.setViewportSize({ width: celular ? 390 : 1440, height: alto })
  await simularSerie(page, serieLarga)
  await page.goto('/cumplimiento')
  const diferencia = page.getByRole('region', { name: 'Promedio por corte' }).locator('dd').last()
  await expect(diferencia).toBeVisible()
  const caja = await diferencia.boundingBox()
  expect(caja!.y + caja!.height).toBeLessThan(alto - 56)
  expect(await page.evaluate(() => scrollY)).toBe(0)
  expect(await page.evaluate(() => document.documentElement.scrollHeight <= 2 * innerHeight)).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const meses = page.locator('section[aria-labelledby="titulo-serie"] ol li')
  await expect(meses).toHaveCount(6)
  await expect(meses.first()).toContainText('julio de 2026')
  await expect(page.getByRole('table', { name: 'Datos de la serie mensual de cumplimiento' })).toBeHidden()
  await page.getByRole('button', { name: 'Ver los 12 meses' }).click()
  await expect(meses).toHaveCount(12)
  await expect(meses.first()).toContainText('enero de 2026')
})
