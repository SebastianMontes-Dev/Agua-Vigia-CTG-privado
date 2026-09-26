import { expect, test } from '@playwright/test'
import { AxeBuilder } from '@axe-core/playwright'
import { mkdirSync } from 'node:fs'
const tamanos = [[360, 780], [375, 812], [390, 844], [768, 1024], [1024, 768], [1280, 800], [1440, 900]] as const
test('revisión de cada composición, tema, foco, objetivos y ausencia de desbordes', async ({ page }) => {
  test.setTimeout(180_000)
  mkdirSync('../docs/qa/f2/capturas', { recursive: true })
  for (const [ancho, alto] of tamanos) {
    await page.setViewportSize({ width: ancho, height: alto })
    for (const tema of ['light', 'dark'] as const) {
      await page.emulateMedia({ colorScheme: tema, reducedMotion: 'reduce' })
      await page.goto('/')
      await expect(page.locator('[data-estados-listos]')).toBeVisible({ timeout: 20_000 })
      await page.screenshot({ path: `../docs/qa/f2/capturas/mapa-${ancho}-${tema}.png` })
      await page.goto('/sectores/zona-industrial')
      await expect(page.locator('.ficha')).toContainText('Sin datos verificados')
      await expect(page.locator('.ficha')).toContainText('Sin dato censal')
      await expect(page.locator('[data-estados-listos]')).toBeVisible({ timeout: 20_000 })
      await page.screenshot({ path: `../docs/qa/f2/capturas/ficha-${ancho}-${tema}.png` })
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
      await page.getByRole('button', { name: 'Reportar en este barrio' }).focus()
      await page.keyboard.press('Enter')
      await expect(page.getByRole('dialog')).toBeVisible()
      await page.screenshot({ path: `../docs/qa/f2/capturas/reporte-${ancho}-${tema}.png` })
      expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
      for (let paso = 0; paso < 8; paso++) {
        await page.keyboard.press('Tab')
        expect(await page.evaluate(() => !!document.activeElement?.closest('[role="dialog"]'))).toBe(true)
      }
      await page.keyboard.press('Escape')
      await expect(page.getByRole('button', { name: 'Reportar en este barrio' })).toBeFocused()
      const pequenos = await page.locator('button:visible,input:visible,select:visible,summary:visible').evaluateAll((elementos) => elementos.filter((elemento) => { const cuadro = elemento.getBoundingClientRect(); return cuadro.width < 44 || cuadro.height < 44 }).map((elemento) => elemento.outerHTML.slice(0, 100)))
      expect(pequenos).toEqual([])
      await page.goto('/confirmar/no-existe')
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('¿También te pasa?')
      await page.screenshot({ path: `../docs/qa/f2/capturas/confirmar-${ancho}-${tema}.png` })
      expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
    }
  }
})
test('carga de geometría única, último listado offline y verificación antigua', async ({ page, context }) => {
  let geometria = 0
  page.on('request', (peticion) => { if (peticion.url().endsWith('/api/sectores/geometria')) geometria++ })
  await page.goto('/')
  await expect(page.locator('[data-estados-listos]')).toBeVisible({ timeout: 20_000 })
  await page.reload()
  await expect(page.locator('[data-estados-listos]')).toBeVisible({ timeout: 20_000 })
  expect(geometria).toBe(1)
  await page.getByLabel('Busca tu barrio').fill('alameda')
  await expect(page.locator('.lista-barrios')).toContainText('Sin verificación reciente')
  await context.setOffline(true)
  await expect(page.getByText(/Sin conexión. Último listado guardado:/)).toBeVisible()
  await page.getByRole('button', { name: 'Reportar', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Tu reporte no se guardó')
  await context.setOffline(false)
})
test('pestaña oculta no inicia solicitudes y vuelve sin duplicar el listado', async ({ page, context }) => {
  await page.goto('/')
  await expect(page.locator('[data-estados-listos]')).toBeVisible({ timeout: 20_000 })
  const otra = await context.newPage()
  await otra.goto('about:blank')
  await otra.bringToFront()
  // Headless no aplica oclusión: se reproduce la propiedad que usa la API de visibilidad.
  await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => true }); Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }); document.dispatchEvent(new Event('visibilitychange')) })
  let peticiones = 0
  page.on('request', () => peticiones++)
  await page.waitForTimeout(16_000)
  expect(peticiones).toBe(0)
  await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => false }); Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' }); document.dispatchEvent(new Event('visibilitychange')) })
  await expect(page.getByText('En vivo', { exact: true })).toBeVisible()
})
