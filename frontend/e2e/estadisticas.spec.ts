import { expect, test, type Page } from '@playwright/test'
import { estadisticasDeEjemplo } from '../src/pruebas/datos/historia'
import { problema, simularApi } from './api-simulada'

function simularEstadisticas(page: Page, cuerpo: unknown) {
  return simularApi(page, { estadisticas: (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(cuerpo) }) })
}

test('debeTitularConLaDuracionPromedioYNombrarLosDiasExtremos', async ({ page }) => {
  await simularApi(page)
  await page.goto('/estadisticas')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Un corte dura 5 horas en promedio')
  await expect(page.getByRole('main')).toHaveCount(1)
  const cifras = page.locator('dl')
  await expect(cifras.getByText('22', { exact: true })).toBeVisible()
  await expect(cifras.getByText('Miércoles', { exact: true })).toBeVisible()
  await expect(cifras.getByText('Domingo', { exact: true })).toBeVisible()
  const dias = page.getByRole('list', { name: 'Cortes de lunes a domingo' }).getByRole('listitem')
  await expect(dias).toHaveCount(7)
  await expect(dias.first()).toContainText('Lun')
  await expect(dias.last()).toContainText('Dom')
})

test('debeGuardarTablasYCsvDetrasDeVerDatos', async ({ page }) => {
  await simularApi(page)
  await page.goto('/estadisticas')
  const tablaDias = page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' })
  await expect(tablaDias).toBeHidden()
  await page.getByText('Ver datos').click()
  await expect(tablaDias.getByRole('rowheader')).toHaveText(['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'])
  const barrios = page.getByRole('table', { name: 'Avisos de corte aprobados por barrio' })
  await expect(barrios.getByRole('rowheader')).toHaveText(['Torices', 'Getsemani', 'Bocagrande'])
  await expect(page.getByRole('link', { name: 'Descargar en CSV' })).toHaveAttribute('href', '/api/estadisticas/exportar.csv')
})

test('debeUsarElNombreDeLaRespuestaYElIdComoRespaldo', async ({ page }) => {
  await simularEstadisticas(page, {
    sectoresMasAfectados: [
      { sectorId: 'armenia', nombre: 'ARMENIA', cantidadCortes: 3 },
      { sectorId: 'barrio-nuevo', nombre: 'Desconocido', cantidadCortes: 2 },
    ],
    cortesPorDiaDeSemana: { Lunes: 3, Martes: 2, Miércoles: 0, Jueves: 0, Viernes: 0, Sábado: 0, Domingo: 0 },
    duracionPromedioHoras: 2.5,
  })
  await page.goto('/estadisticas')
  const barrios = page.locator('section[aria-labelledby="titulo-barrios"] ol li')
  await expect(barrios).toHaveText([/^Armenia/, /^barrio-nuevo/])
})

test('debeDecirSinRodeosQueNingunBoletinNombraBarrios', async ({ page }) => {
  await simularEstadisticas(page, {
    sectoresMasAfectados: [],
    cortesPorDiaDeSemana: { Lunes: 1, Martes: 0, Miércoles: 0, Jueves: 0, Viernes: 0, Sábado: 0, Domingo: 0 },
    duracionPromedioHoras: 2.5,
  })
  await page.goto('/estadisticas')
  await expect(page.getByText('Ningún boletín aprobado de Acuacar menciona barrios todavía.')).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Un corte dura 3 horas en promedio')
})

test('debeTitularLaPreguntaYExplicarLaAusenciaSinDatos', async ({ page }) => {
  await simularEstadisticas(page, {
    sectoresMasAfectados: [],
    cortesPorDiaDeSemana: { Lunes: 0, Martes: 0, Miércoles: 0, Jueves: 0, Viernes: 0, Sábado: 0, Domingo: 0 },
    duracionPromedioHoras: 0,
  })
  await page.goto('/estadisticas')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Cuándo hay cortes en Cartagena')
  await expect(page.getByText('Todavía no hay datos registrados para resumir.')).toBeVisible()
  await expect(page.getByRole('list', { name: 'Cortes de lunes a domingo' })).toHaveCount(0)
})

test('debePermitirReintentarDespuesDeUnError', async ({ page }) => {
  let intentos = 0
  await simularApi(page, { estadisticas: (route) => {
    intentos++
    return intentos < 5
      ? route.fulfill(problema(503, 'fallo-temporal', 'Fallo temporal'))
      : route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(estadisticasDeEjemplo) })
  } })
  await page.goto('/estadisticas')
  await expect(page.getByRole('alert')).toContainText('No pudimos consultar las estadísticas', { timeout: 20_000 })
  await page.getByRole('button', { name: 'Reintentar' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Un corte dura 5 horas en promedio')
})

test('debeResponderEnLaPrimeraVistaConElGraficoDeDias', async ({ page }) => {
  const alto = (page.viewportSize()?.width ?? 0) < 600 ? 844 : 900
  await page.setViewportSize({ width: (page.viewportSize()?.width ?? 0) < 600 ? 390 : 1440, height: alto })
  await simularEstadisticas(page, {
    ...estadisticasDeEjemplo,
    sectoresMasAfectados: [
      ...estadisticasDeEjemplo.sectoresMasAfectados,
      { sectorId: 'manga', nombre: 'MANGA', cantidadCortes: 2 },
      { sectorId: 'armenia', nombre: 'ARMENIA', cantidadCortes: 1 },
      { sectorId: 'crespo', nombre: 'CRESPO', cantidadCortes: 1 },
    ],
  })
  await page.goto('/estadisticas')
  const grafico = page.getByRole('list', { name: 'Cortes de lunes a domingo' })
  await expect(grafico).toBeVisible()
  const caja = await grafico.boundingBox()
  expect(caja!.y + caja!.height).toBeLessThan(alto - 56)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollHeight <= 2 * innerHeight)).toBe(true)
  const barrios = page.locator('section[aria-labelledby="titulo-barrios"] ol li')
  await expect(barrios).toHaveCount(5)
  await page.getByRole('button', { name: 'Ver los 6 barrios' }).click()
  await expect(barrios).toHaveCount(6)
})
