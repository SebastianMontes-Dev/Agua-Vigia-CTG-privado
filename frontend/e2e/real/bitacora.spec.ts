import { expect, test } from '@playwright/test'

test('debeMostrarEventosYFiltrarlosPorTipoConLaApiReal', async ({ page }) => {
  const escrituras: string[] = []
  page.on('request', (peticion) => {
    if (peticion.url().includes('/api/') && peticion.method() !== 'GET') escrituras.push(peticion.method())
  })
  await page.goto('/bitacora')
  await expect(page.getByRole('heading', { name: 'Bitácora' })).toBeVisible()
  const eventos = page.locator('section[aria-label="Eventos de la bitácora"] ol > li')
  await expect(eventos.first()).toBeVisible()

  await page.getByLabel('Tipo de evento').selectOption('CORTE_CONFIRMADO_POR_CIUDADANOS')
  await expect(eventos.first()).toBeVisible()
  expect(await eventos.count()).toBeGreaterThan(0)
  for (const evento of await eventos.all()) {
    await expect(evento.getByText('Confirmado por ciudadanos')).toBeVisible()
  }
  expect(escrituras).toEqual([])
})
