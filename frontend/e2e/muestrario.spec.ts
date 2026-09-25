import { expect, test, type Page } from '@playwright/test'

function fondoDelCuerpo(page: Page) {
  return page.evaluate(() => getComputedStyle(document.body).backgroundColor)
}

test('debeAplicarLosTokensEnElTemaClaroYEnElOscuro', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1, name: 'AguaVigía CTG' })).toBeVisible()
  expect(await fondoDelCuerpo(page)).toBe('rgb(242, 247, 246)')

  await page.getByRole('radio', { name: 'Oscuro' }).check()
  expect(await fondoDelCuerpo(page)).toBe('rgb(6, 28, 35)')

  await page.reload()
  expect(await fondoDelCuerpo(page)).toBe('rgb(6, 28, 35)')
})

test('debeSeguirAlSistemaYDejarQueElInterruptorLoContradiga', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.goto('/')
  expect(await fondoDelCuerpo(page)).toBe('rgb(6, 28, 35)')

  await page.getByRole('radio', { name: 'Claro' }).check()
  expect(await fondoDelCuerpo(page)).toBe('rgb(242, 247, 246)')
})

test('noDebeHacerScrollHorizontalEnElCuerpo', async ({ page }) => {
  await page.goto('/')
  const desborda = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  )
  expect(desborda).toBe(false)
})
