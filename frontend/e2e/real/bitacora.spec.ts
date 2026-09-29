import { expect, test } from '@playwright/test'

test('debeMostrarEventosYFiltrarlosPorTipoConLaApiReal', async ({ page }) => {
  const escrituras: string[] = []
  page.on('request', (peticion) => {
    if (peticion.url().includes('/api/') && peticion.method() !== 'GET') escrituras.push(peticion.method())
  })
  await page.goto('/bitacora')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(/^(El último cambio fue hace|Cada cambio del agua)/)
  const eventos = page.locator('section[aria-label="Eventos de la bitácora"] ol > li')
  await expect(eventos.first()).toBeVisible()

  await page.getByRole('button', { name: /^Confirmados por vecinos \d+$/ }).click()
  await expect(page.getByRole('button', { name: /^Confirmados por vecinos/ })).toHaveAttribute('aria-pressed', 'true')
  await expect(eventos.first()).toBeVisible()
  for (const evento of await eventos.all()) {
    await expect(evento.getByText('Confirmado por ciudadanos', { exact: false })).toBeVisible()
  }
  expect(escrituras).toEqual([])
})
