import { expect, test } from '@playwright/test'
import { bitacoraDeEjemplo } from '../src/pruebas/datos/historia'
import { simularApi } from './api-simulada'

test('debeFiltrarPorTipoConPestanasYConvertirLosDiasDeCartagena', async ({ page }) => {
  await simularApi(page)
  const consultas: URL[] = []
  page.on('request', (peticion) => {
    const url = new URL(peticion.url())
    if (url.pathname === '/api/bitacora' && url.searchParams.get('tamano') !== '1') consultas.push(url)
  })
  await page.goto('/bitacora')
  await expect(page.getByRole('button', { name: 'Restablecidos 2' })).toBeVisible()
  await page.getByRole('button', { name: 'Restablecidos 2' }).click()
  await expect(page.getByRole('button', { name: 'Restablecidos 2' })).toHaveAttribute('aria-pressed', 'true')
  await page.getByText('Barrio y fechas').click()
  await page.getByLabel('Desde (día de Cartagena)').fill('2026-09-24')
  await page.getByLabel('Hasta (inclusive, día de Cartagena)').fill('2026-09-25')
  await expect(page.getByText('Servicio restablecido en Bocagrande', { exact: false })).toBeVisible()
  await expect.poll(() => consultas.at(-1)?.searchParams.get('desde')).toBe('2026-09-24T05:00:00Z')
  expect(consultas.at(-1)?.searchParams.get('hasta')).toBe('2026-09-26T05:00:00Z')
  expect(consultas.at(-1)?.searchParams.get('tipo')).toBe('CORTE_RESTABLECIDO')
  await expect(page).toHaveURL(/tipo=CORTE_RESTABLECIDO/)
})

test('debeConsultarElSustentoSoloAlAbrirloYNoNombrarLoQueNoExiste', async ({ page }) => {
  await simularApi(page)
  let consultas = 0
  page.on('request', (peticion) => { if (new URL(peticion.url()).pathname.endsWith('/sustento')) consultas++ })
  await page.goto('/bitacora')
  await expect(page.getByRole('button', { name: 'Sustentado por 5 reportes' })).toBeVisible()
  await expect(page.getByText('Informativo')).toHaveCount(0)
  await expect(page.getByText('Sin enlace a la fuente')).toHaveCount(0)
  expect(consultas).toBe(0)
  await page.getByRole('button', { name: 'Sustentado por 5 reportes' }).click()
  await expect(page.getByText('reporte-1')).toBeVisible()
  expect(consultas).toBe(1)
  await expect(page.getByText('Referencias de trazabilidad; el contenido de los reportes no es público.')).toBeVisible()
})

test('debeTitularConElUltimoCambioYResponderEnLaPrimeraVista', async ({ page }) => {
  const celular = (page.viewportSize()?.width ?? 0) < 600
  const alto = celular ? 844 : 900
  await page.setViewportSize({ width: celular ? 390 : 1440, height: alto })
  const ahora = Date.now()
  const eventos = Array.from({ length: 20 }, (_, indice) => ({
    ...bitacoraDeEjemplo[indice % bitacoraDeEjemplo.length],
    id: `evento-${indice}`,
    timestamp: new Date(ahora - (indice + 1) * 25 * 60_000).toISOString(),
  }))
  await simularApi(page, { bitacora: (route) => {
    const url = new URL(route.request().url())
    const pagina = Number(url.searchParams.get('pagina') ?? 0)
    const tamano = Number(url.searchParams.get('tamano') ?? 10)
    return route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify(eventos.slice(pagina * tamano, (pagina + 1) * tamano)),
      headers: { 'X-Total-Count': '20', 'X-Total-Pages': String(Math.ceil(20 / tamano)), 'X-Page': String(pagina), 'X-Page-Size': String(tamano) },
    })
  } })
  await page.goto('/bitacora')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(/^El último cambio fue hace 2\d min/)
  await expect(page.getByText(/Bitácora · 20 eventos/)).toBeVisible()
  const visibles = page.locator('section[aria-label="Eventos de la bitácora"] ol > li')
  const lote = celular ? 6 : 10
  await expect(visibles).toHaveCount(lote)
  const primero = await visibles.first().boundingBox()
  expect(primero!.y + primero!.height).toBeLessThan(alto - 56)
  expect(await page.evaluate(() => scrollY)).toBe(0)
  expect(await page.evaluate(() => document.documentElement.scrollHeight <= 2 * innerHeight)).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: 'Cargar más eventos' }).click()
  await expect(visibles).toHaveCount(2 * lote)
})

test('debeAbrirBarrioYFechasSiLaUrlTraeUno', async ({ page }) => {
  await simularApi(page)
  await page.goto('/bitacora?desde=2026-09-24')
  await expect(page.locator('details').filter({ hasText: 'Barrio y fechas' })).toHaveAttribute('open', '')
  await expect(page.getByLabel('Desde (día de Cartagena)')).toHaveValue('2026-09-24')
})
