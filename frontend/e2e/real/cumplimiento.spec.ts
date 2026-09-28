import { expect, test } from '@playwright/test'

test('muestra índice global colombiano y al menos un mes medido', async ({ page }) => {
  await page.goto('/cumplimiento')
  await expect(page.getByRole('heading', { name: 'Lo prometido y lo que duró' })).toBeVisible()
  const indice = page.locator('section[aria-label="Comparación entre lo prometido y lo real"]')
  await expect(indice).toBeVisible()
  await expect(indice).toContainText(/\d+,\d+%/)
  await expect(page.getByText(/sobre \d+ cortes/).first()).toBeVisible()
})
