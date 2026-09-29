import { expect, test } from '@playwright/test'

test('debeMostrarIndiceGlobalColombianoYAlMenosUnMesMedido', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/cumplimiento')
  await expect(page.getByRole('heading', { name: 'Lo prometido y lo que duró' })).toBeVisible()
  const indice = page.locator('section[aria-label="Comparación entre lo prometido y lo real"]')
  await expect(indice).toBeVisible()
  const porcentaje = await indice.getByText('Índice de cumplimiento').locator('..').locator('strong').textContent()
  expect(porcentaje?.replace(/[\u00a0\u202f]/g, ' ').trim()).toMatch(/^\d{1,3}(,\d)?\s?%$/)
  await expect(page.getByText(/sobre \d+ cortes?/).first()).toBeVisible()
})
