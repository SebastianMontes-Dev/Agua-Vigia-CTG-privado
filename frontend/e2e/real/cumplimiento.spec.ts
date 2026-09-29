import { expect, test } from '@playwright/test'

test('debeTitularConElVeredictoYPromediarPorCorteConLaApiReal', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/cumplimiento')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(/^Los cortes (terminan antes de|duran|duran más de) lo anunciado$/)
  await expect(page.getByText(/^Cumplimiento · \d+ cortes? cerrados?/)).toBeVisible()
  const cifras = page.getByRole('region', { name: 'Promedio por corte' })
  await expect(cifras.locator('dd')).toHaveCount(3)
  const indice = await cifras.getByText(/Índice/).textContent()
  expect(indice?.replace(/[  ]/g, ' ').trim()).toMatch(/^Índice \d{1,3}(,\d)?\s?%$/)
  await expect(page.locator('section[aria-labelledby="titulo-serie"] ol li').first()).toContainText(/\d+ cortes?/)
})
