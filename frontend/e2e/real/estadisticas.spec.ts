import { expect, test } from '@playwright/test'

test('debeMostrarLosDiasOExplicarSuAusenciaConLaApiReal', async ({ page, request }) => {
  const respuesta = await request.get('/api/estadisticas')
  expect(respuesta.status()).toBe(200)
  const datos = await respuesta.json() as { cortesPorDiaDeSemana?: Record<string, number>; duracionPromedioHoras?: number | null }
  const totalCortes = Object.values(datos.cortesPorDiaDeSemana ?? {}).reduce((total, cantidad) => total + cantidad, 0)
  await page.goto('/estadisticas')
  await expect(page.getByRole('heading', { level: 1, name: 'Estadísticas' })).toBeVisible()
  if (totalCortes === 0) {
    await expect(page.getByText('Todavía no hay cortes registrados.')).toBeVisible()
    await expect(page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' })).toHaveCount(0)
  } else {
    await page.locator('section[aria-labelledby="titulo-dias"] summary').click()
    const dias = page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' })
    await expect(dias.getByRole('rowheader')).toHaveText([
      'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo',
    ])
  }
  if (!datos.duracionPromedioHoras) await expect(page.getByText('Aún no hay cortes cerrados para medir')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Barrios que más aparecen en avisos de corte' })).toBeVisible()
  await expect(page.getByText('Duración media de los cortes:')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Descargar estadísticas en CSV' }))
    .toHaveAttribute('href', '/api/estadisticas/exportar.csv')
})

test('debeResponderElCsvRealComoArchivoDeTexto', async ({ request }) => {
  const respuesta = await request.get('/api/estadisticas/exportar.csv')
  expect(respuesta.status()).toBe(200)
  expect(respuesta.headers()['content-type']).toMatch(/^text\/csv(?:;|$)/i)
})
