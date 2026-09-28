import { expect, test } from '@playwright/test'
import { cumplimientoDecimalDeEjemplo, serieDeEjemplo } from '../src/pruebas/datos/historia'
import { problema, simularApi } from './api-simulada'

const serieLarga = [
  { ...serieDeEjemplo[0]!, periodo: '2026-02' },
  { ...serieDeEjemplo[0]!, periodo: '2026-03' },
  ...serieDeEjemplo,
]

function simularSerieLarga(page: Parameters<typeof simularApi>[0]) {
  return simularApi(page, { serie: (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(serieLarga),
  }) })
}

test('debePresentarElIndiceYLaSerieSinRellenarMesesAusentes', async ({ page }) => {
  await simularApi(page)
  await page.goto('/cumplimiento')
  await expect(page.getByRole('heading', { name: 'Lo prometido y lo que duró' })).toBeVisible()
  await expect(page.getByText('En total, los cortes cerrados duraron 2 horas y 30 minutos más de lo anunciado.')).toBeVisible()
  await expect(page.getByRole('main')).toHaveCount(1)
  await expect(page.getByText('Índice de cumplimiento').locator('..').locator('strong')).toHaveText('80%')
  await expect(page.getByText('marzo de 2026')).toHaveCount(0)
  if ((page.viewportSize()?.width ?? 0) < 768) {
    await expect(page.locator('section[aria-labelledby="titulo-serie"] ol')).toBeVisible()
  } else {
    await expect(page.getByRole('table', { name: 'Datos de la serie mensual de cumplimiento' })).toBeVisible()
  }
})

test('debeMostrarAusenciaAnte400SinInventarPorcentaje', async ({ page }) => {
  await simularApi(page, { cumplimiento: (route) => route.fulfill(problema(400, 'peticion-invalida', 'No hay cortes cerrados')) })
  await page.goto('/cumplimiento')
  await expect(page.getByText(/Aún no hay cortes cerrados para medir/)).toBeVisible()
  await expect(page.getByText('0%', { exact: true })).toHaveCount(0)
})

test('debePresentarUnIndiceDecimalConComaColombiana', async ({ page }) => {
  await simularApi(page, { cumplimiento: (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(cumplimientoDecimalDeEjemplo),
  }) })
  await page.goto('/cumplimiento')
  await expect(page.getByText('Índice de cumplimiento').locator('..').locator('strong')).toHaveText('66,7%')
  await expect(page.getByText('66.7%')).toHaveCount(0)
})

test('debeAplicarBarrioAlAgregadoYFechasSoloALaSerieYCSV', async ({ page }) => {
  await simularApi(page)
  const consultas: URL[] = []
  page.on('request', (peticion) => {
    const url = new URL(peticion.url())
    if (url.pathname.startsWith('/api/cumplimiento')) consultas.push(url)
  })
  await page.goto('/cumplimiento')
  await page.getByLabel('Barrio').fill('manga')
  await page.getByRole('option', { name: 'Manga' }).click()
  if ((page.viewportSize()?.width ?? 0) < 768) await page.getByText('Filtrar por fechas').click()
  await page.getByLabel('Desde (día de Cartagena)').fill('2026-04-01')
  await page.getByLabel('Hasta (inclusive, día de Cartagena)').fill('2026-06-30')
  await expect.poll(() => consultas.findLast((url) => url.pathname === '/api/cumplimiento/serie')?.searchParams.get('hasta')).toBe('2026-07-01T04:59:59.999Z')
  const agregado = consultas.findLast((url) => url.pathname.endsWith('/sectores/manga'))
  expect(agregado?.search).toBe('')
  const serie = consultas.findLast((url) => url.pathname === '/api/cumplimiento/serie')
  expect(serie?.searchParams.get('sectorId')).toBe('manga')
  expect(serie?.searchParams.get('desde')).toBe('2026-04-01T05:00:00.000Z')
  const csv = new URL(await page.getByRole('link', { name: 'Descargar serie en CSV' }).getAttribute('href') ?? '', 'http://localhost:4173')
  expect(csv.searchParams.toString()).toBe(serie?.searchParams.toString())
})

test('debeDistinguirBarrioDesconocidoDeAusenciaDeCortes', async ({ page }) => {
  await simularApi(page, { cumplimiento: (route) => route.fulfill(problema(400, 'peticion-invalida', 'No hay cortes cerrados')) })
  await page.goto('/cumplimiento?sector=no-existe')
  await expect(page.getByText('No encontramos este barrio.')).toBeVisible()
  await expect(page.getByText(/Aún no hay cortes cerrados para medir/)).toHaveCount(0)
})

test('debeMostrarElIndiceEnLaPrimeraPantallaDelCelular', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await simularApi(page)
  await page.goto('/cumplimiento')
  const indice = page.getByText('Índice de cumplimiento').locator('..').locator('strong')
  await expect(indice).toBeVisible()
  const caja = await indice.boundingBox()
  expect(caja).not.toBeNull()
  expect(caja!.y + caja!.height).toBeLessThan(844 - 56)
  expect(await page.evaluate(() => scrollY)).toBe(0)
})

test('debeLimitarLaListaMovilASeisMesesHastaPulsarVerTodos', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await simularSerieLarga(page)
  await page.goto('/cumplimiento')
  await expect(page.getByRole('table', { name: 'Datos de la serie mensual de cumplimiento' })).toBeHidden()
  const filas = page.locator('section[aria-labelledby="titulo-serie"] ol li')
  await expect(filas).toHaveCount(6)
  await expect(filas.first()).toContainText('abril de 2026')
  await page.getByRole('button', { name: 'Ver todos los meses (8)' }).click()
  await expect(filas).toHaveCount(8)
  await expect(filas.first()).toContainText('febrero de 2026')
})

test('debeMostrarLaTablaCompletaSinListaEnEscritorio', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await simularSerieLarga(page)
  await page.goto('/cumplimiento')
  const tabla = page.getByRole('table', { name: 'Datos de la serie mensual de cumplimiento' })
  await expect(tabla).toBeVisible()
  await expect(tabla.getByRole('rowheader')).toHaveCount(8)
  await expect(page.locator('section[aria-labelledby="titulo-serie"] ol')).toBeHidden()
  await expect(page.getByLabel('Desde (día de Cartagena)')).toBeVisible()
})
