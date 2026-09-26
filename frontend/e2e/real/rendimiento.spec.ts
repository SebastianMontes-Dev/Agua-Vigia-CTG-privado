import { expect, test } from '@playwright/test'
test('3G fría muestra todos los polígonos con estado antes de tres segundos', async ({ page, context }) => {
  const conexion = await context.newCDPSession(page)
  await conexion.send('Network.enable')
  await conexion.send('Network.setCacheDisabled', { cacheDisabled: true })
  await conexion.send('Network.emulateNetworkConditions', { offline: false, latency: 150, downloadThroughput: 1_600_000 / 8, uploadThroughput: 750_000 / 8, connectionType: 'cellular3g' })
  await page.goto('/', { waitUntil: 'commit' })
  await expect(page.locator('[data-estados-iniciales]')).toBeVisible({ timeout: 20_000 })
  const milisegundos = await page.evaluate(() => performance.getEntriesByName('estados-pintados')[0]?.startTime ?? Infinity)
  await expect(page.locator('[data-estados-iniciales] > svg > path')).toHaveCount(211)
  await test.info().attach('medicion-3g.json', { body: JSON.stringify({ milisegundos, bajadaMbps: 1.6, subidaMbps: .75, latenciaMs: 150, cache: 'fria', representacion: 'SVG inicial; MapLibre progresivo' }), contentType: 'application/json' })
  expect(milisegundos).toBeLessThan(3000)
})
