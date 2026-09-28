import { expect, test } from '@playwright/test'
import { problema, simularApi } from './api-simulada'

test('presenta la conclusión y la serie sin rellenar meses ausentes', async ({ page }) => {
  await simularApi(page)
  await page.goto('/cumplimiento')
  await expect(page.getByRole('heading', { name: 'Lo prometido y lo que duró' })).toBeVisible()
  await expect(page.getByText(/Prometieron 10 horas. Fueron 12 horas y media/)).toBeVisible()
  await expect(page.getByText('sobre 4 cortes').first()).toBeVisible()
  await expect(page.getByText('marzo de 2026')).toHaveCount(0)
  await expect(page.getByRole('table', { name: 'Datos de la serie mensual de cumplimiento' })).toBeVisible()
})

test('muestra ausencia ante 400 sin inventar porcentaje', async ({ page }) => {
  await simularApi(page, { cumplimiento: (route) => route.fulfill(problema(400, 'peticion-invalida', 'No hay cortes cerrados')) })
  await page.goto('/cumplimiento')
  await expect(page.getByText(/Aún no hay cortes cerrados para medir/)).toBeVisible()
  await expect(page.getByText('0%', { exact: true })).toHaveCount(0)
})

test('aplica barrio a agregado y serie, y fechas solo a serie y CSV', async ({ page }) => {
  await simularApi(page)
  const consultas: URL[] = []
  page.on('request', (peticion) => {
    const url = new URL(peticion.url())
    if (url.pathname.startsWith('/api/cumplimiento')) consultas.push(url)
  })
  await page.goto('/cumplimiento')
  await page.getByLabel('Barrio').fill('manga')
  await page.getByRole('option', { name: 'Manga' }).click()
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
