import { expect, test } from '@playwright/test'
import { bitacoraDeEjemplo } from '../src/pruebas/datos/historia'
import { simularApi } from './api-simulada'

test('debeFiltrarElHistorialYConvertirLosDiasDeCartagena', async ({ page }) => {
  await simularApi(page)
  const consultas: URL[] = []
  page.on('request', (peticion) => {
    const url = new URL(peticion.url())
    if (url.pathname === '/api/bitacora') consultas.push(url)
  })
  await page.goto('/bitacora')
  await expect(page.getByRole('heading', { name: 'Bitácora' })).toBeVisible()
  if ((page.viewportSize()?.width ?? 0) < 768) await page.getByText('Filtrar eventos').click()
  await page.getByLabel('Tipo de evento').selectOption('CORTE_RESTABLECIDO')
  await page.getByLabel('Desde (día de Cartagena)').fill('2026-09-24')
  await page.getByLabel('Hasta (inclusive, día de Cartagena)').fill('2026-09-25')
  await expect(page.getByText('Servicio restablecido en Bocagrande', { exact: false })).toBeVisible()
  await expect.poll(() => consultas.at(-1)?.searchParams.get('desde')).toBe('2026-09-24T05:00:00Z')
  expect(consultas.at(-1)?.searchParams.get('hasta')).toBe('2026-09-26T05:00:00Z')
  expect(consultas.at(-1)?.searchParams.get('tipo')).toBe('CORTE_RESTABLECIDO')
  await expect(page).toHaveURL(/tipo=CORTE_RESTABLECIDO/)
})

test('debeConsultarElSustentoSoloAlAbrirloYTolerarEstadoNulo', async ({ page }) => {
  await simularApi(page)
  let consultas = 0
  page.on('request', (peticion) => { if (new URL(peticion.url()).pathname.endsWith('/sustento')) consultas++ })
  await page.goto('/bitacora')
  await expect(page.getByText('Informativo')).toBeVisible()
  expect(consultas).toBe(0)
  await page.getByRole('button', { name: 'Ver referencias de sustento' }).click()
  await expect(page.getByText('reporte-1')).toBeVisible()
  expect(consultas).toBe(1)
  await expect(page.getByText('Referencias de trazabilidad; el contenido de los reportes no es público.')).toBeVisible()
})

test('debeMostrarCincoDeVeinteEventosYResponderEnLaPrimeraPantalla', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const eventos = Array.from({ length: 20 }, (_, indice) => ({
    ...bitacoraDeEjemplo[indice % bitacoraDeEjemplo.length],
    id: `evento-${indice}`,
    timestamp: new Date(Date.UTC(2026, 8, 25, 20 - indice)).toISOString(),
  }))
  await simularApi(page, { bitacora: (route) => {
    const url = new URL(route.request().url())
    const pagina = Number(url.searchParams.get('pagina') ?? 0)
    const tamano = Number(url.searchParams.get('tamano') ?? 5)
    return route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify(eventos.slice(pagina * tamano, (pagina + 1) * tamano)),
      headers: { 'X-Total-Count': '20', 'X-Total-Pages': '4', 'X-Page': String(pagina), 'X-Page-Size': String(tamano) },
    })
  } })
  await page.goto('/bitacora')
  await expect(page.getByRole('main')).toHaveCount(1)
  await expect(page.getByText('20 eventos con estos filtros')).toBeVisible()
  const visibles = page.locator('section[aria-label="Eventos de la bitácora"] ol > li')
  await expect(visibles).toHaveCount(5)
  const primero = await visibles.first().boundingBox()
  expect(primero).not.toBeNull()
  expect(primero!.y + primero!.height).toBeLessThan(844 - 56)
  expect(await page.evaluate(() => scrollY)).toBe(0)
  const { alto, altoVentana } = await page.evaluate(() => ({ alto: document.documentElement.scrollHeight, altoVentana: innerHeight }))
  expect(alto).toBeLessThanOrEqual(2 * altoVentana)
  await page.getByRole('button', { name: 'Cargar más' }).click()
  await expect(visibles).toHaveCount(10)
})

test('debeAbrirLosFiltrosSiLaUrlTraeUno', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await simularApi(page)
  await page.goto('/bitacora?tipo=CORTE_RESTABLECIDO')
  await expect(page.locator('details').filter({ hasText: 'Filtrar eventos' })).toHaveAttribute('open', '')
  await expect(page.getByLabel('Tipo de evento')).toHaveValue('CORTE_RESTABLECIDO')
})
