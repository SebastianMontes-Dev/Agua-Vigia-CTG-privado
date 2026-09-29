import { expect, test } from '@playwright/test'
import { estadisticasDeEjemplo } from '../src/pruebas/datos/historia'
import { problema, simularApi } from './api-simulada'

test('debeMostrarBarriosDiasOrdenadosDuracionYTablasConDatosDeEjemplo', async ({ page }) => {
  await simularApi(page)
  await page.goto('/estadisticas')

  await expect(page.getByRole('heading', { level: 1, name: 'Estadísticas' })).toBeVisible()
  await expect(page.getByRole('main')).toHaveCount(1)
  await expect(page.getByText('5,4 horas')).toBeVisible()
  await page.locator('section[aria-labelledby="titulo-sectores"] summary').click()
  await page.locator('section[aria-labelledby="titulo-dias"] summary').click()
  const barrios = page.getByRole('table', { name: 'Avisos de corte aprobados por barrio' })
  await expect(barrios.getByRole('rowheader')).toHaveText(['Torices', 'Getsemani', 'Bocagrande'])
  await expect(barrios.getByRole('columnheader')).toHaveText(['Barrio', 'Avisos'])
  await expect(page.getByText('Veces que el barrio aparece en un boletín de Acuacar aprobado.', { exact: false })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Cumplimiento' }).last()).toHaveAttribute('href', '/cumplimiento')
  const dias = page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' })
  await expect(dias.getByRole('rowheader')).toHaveText([
    'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo',
  ])
  await expect(dias.getByRole('row').last()).toContainText('0')
  await expect(page.getByRole('link', { name: 'Descargar estadísticas en CSV' }))
    .toHaveAttribute('href', '/api/estadisticas/exportar.csv')
  await page.evaluate(() => { (window as Window & { marcaNavegacion?: boolean }).marcaNavegacion = true })
  await page.getByRole('link', { name: 'Cumplimiento' }).last().click()
  await expect(page).toHaveURL(/\/cumplimiento$/)
  expect(await page.evaluate(() => (window as Window & { marcaNavegacion?: boolean }).marcaNavegacion)).toBe(true)
})

test('debeUsarNombreDeLaRespuestaYElIdComoRespaldo', async ({ page }) => {
  await simularApi(page, { estadisticas: (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({
      sectoresMasAfectados: [
        { sectorId: 'armenia', nombre: 'ARMENIA', cantidadCortes: 3 },
        { sectorId: 'barrio-nuevo', nombre: 'Desconocido', cantidadCortes: 2 },
      ],
      cortesPorDiaDeSemana: { Lunes: 3, Martes: 2, Miércoles: 0, Jueves: 0, Viernes: 0, Sábado: 0, Domingo: 0 },
      duracionPromedioHoras: 2.5,
    }),
  }) })
  await page.goto('/estadisticas')

  await page.locator('section[aria-labelledby="titulo-sectores"] summary').click()
  const barrios = page.getByRole('table', { name: 'Avisos de corte aprobados por barrio' })
  await expect(barrios.getByRole('rowheader')).toHaveText(['Armenia', 'barrio-nuevo'])
})

test('debeConservarDiasYDuracionMedidaCuandoNoHayBarriosEnAvisos', async ({ page }) => {
  await simularApi(page, { estadisticas: (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({
      sectoresMasAfectados: [],
      cortesPorDiaDeSemana: { Lunes: 1, Martes: 0, Miércoles: 0, Jueves: 0, Viernes: 0, Sábado: 0, Domingo: 0 },
      duracionPromedioHoras: 2.5,
    }),
  }) })
  await page.goto('/estadisticas')

  await expect(page.getByText('Todavía no hay boletines de Acuacar aprobados que mencionen barrios.')).toBeVisible()
  await expect(page.getByText('2,5 horas')).toBeVisible()
  await page.locator('section[aria-labelledby="titulo-dias"] summary').click()
  const dias = page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' })
  await expect(dias.getByRole('rowheader')).toHaveCount(7)
  await expect(dias.getByRole('cell')).toHaveText(['1', '0', '0', '0', '0', '0', '0'])
  await expect(page.getByText('Todavía no hay datos registrados para resumir.')).toHaveCount(0)
})

test('debeMostrarAusenciasCuandoNoHayAvisosNiCortesMedidos', async ({ page }) => {
  await simularApi(page, { estadisticas: (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({
      sectoresMasAfectados: [],
      cortesPorDiaDeSemana: { Lunes: 0, Martes: 0, Miércoles: 0, Jueves: 0, Viernes: 0, Sábado: 0, Domingo: 0 },
      duracionPromedioHoras: 0,
    }),
  }) })
  await page.goto('/estadisticas')
  await expect(page.getByText('Todavía no hay datos registrados para resumir.')).toBeVisible()
  await expect(page.getByText('Aún no hay cortes cerrados para medir')).toBeVisible()
  await expect(page.getByText('Todavía no hay cortes registrados.')).toBeVisible()
  await expect(page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' })).toHaveCount(0)
  await expect(page.locator('section[aria-labelledby="titulo-dias"] ol li')).toHaveCount(0)
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
  await expect(page.getByText('5,4 horas')).toBeVisible()
})

test('debeEvitarElDesbordamientoHorizontalEnCelular', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await simularApi(page)
  await page.goto('/estadisticas')
  await expect(page.getByText('Ver como tabla')).toHaveCount(2)
  await expect(page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' })).toBeHidden()
  await page.locator('section[aria-labelledby="titulo-dias"] summary').click()
  await expect(page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('debeAbrirLasTablasPlegadasEnEscritorio', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await simularApi(page)
  await page.goto('/estadisticas')
  const barrios = page.getByRole('table', { name: 'Avisos de corte aprobados por barrio' })
  const dias = page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' })
  await expect(page.getByText('Ver como tabla')).toHaveCount(2)
  await expect(barrios).toBeHidden()
  await expect(dias).toBeHidden()
  await page.locator('section[aria-labelledby="titulo-sectores"] summary').click()
  await page.locator('section[aria-labelledby="titulo-dias"] summary').click()
  await expect(barrios.getByRole('rowheader')).toHaveCount(3)
  await expect(dias.getByRole('rowheader')).toHaveCount(7)
})

test('debeResponderEnLaPrimeraVistaYQuedarBajoDosPantallasConCincoBarrios', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await simularApi(page, { estadisticas: (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({
      ...estadisticasDeEjemplo,
      sectoresMasAfectados: [
        ...estadisticasDeEjemplo.sectoresMasAfectados,
        { sectorId: 'manga', nombre: 'MANGA', cantidadCortes: 2 },
        { sectorId: 'armenia', nombre: 'ARMENIA', cantidadCortes: 1 },
      ],
    }),
  }) })
  await page.goto('/estadisticas')
  const duracion = page.getByText('5,4 horas')
  await expect(duracion).toBeVisible()
  const sectores = page.locator('section[aria-labelledby="titulo-sectores"] ol li:visible')
  await expect(sectores).toHaveCount(3)
  const cajaDuracion = await duracion.boundingBox()
  const cajaPrimerSector = await sectores.first().boundingBox()
  expect(cajaDuracion).not.toBeNull()
  expect(cajaPrimerSector).not.toBeNull()
  expect(cajaDuracion!.y + cajaDuracion!.height).toBeLessThan(844 - 56)
  expect(cajaPrimerSector!.y + cajaPrimerSector!.height).toBeLessThan(844 - 56)
  expect(await page.evaluate(() => scrollY)).toBe(0)
  expect(await page.evaluate(() => document.documentElement.scrollHeight <= 2 * innerHeight)).toBe(true)
  await page.getByRole('button', { name: 'Ver todos los barrios (5)' }).click()
  await expect(sectores).toHaveCount(5)
})
