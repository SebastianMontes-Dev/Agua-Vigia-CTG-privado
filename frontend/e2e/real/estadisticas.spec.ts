import { expect, test } from '@playwright/test'

test('debeMostrarLosDiasOExplicarSuAusenciaConLaApiReal', async ({ page, request }) => {
  const respuesta = await request.get('/api/estadisticas')
  expect(respuesta.status()).toBe(200)
  const datos = await respuesta.json() as { cortesPorDiaDeSemana?: Record<string, number>; duracionPromedioHoras?: number | null }
  const totalCortes = Object.values(datos.cortesPorDiaDeSemana ?? {}).reduce((total, cantidad) => total + cantidad, 0)
  await page.goto('/estadisticas')
  const titular = page.getByRole('heading', { level: 1 })
  if (datos.duracionPromedioHoras) await expect(titular).toHaveText(/^Un corte dura \d+ (horas?|minutos?) en promedio$/)
  else await expect(titular).toHaveText('Cuándo hay cortes en Cartagena')
  if (totalCortes > 0) {
    await expect(page.getByRole('list', { name: 'Cortes de lunes a domingo' }).getByRole('listitem')).toHaveCount(7)
    await page.getByText('Ver datos').click()
    await expect(page.getByRole('table', { name: 'Cortes registrados de lunes a domingo' }).getByRole('rowheader')).toHaveText([
      'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo',
    ])
    await expect(page.getByRole('link', { name: 'Descargar en CSV' })).toHaveAttribute('href', '/api/estadisticas/exportar.csv')
  }
})

test('debeResponderElCsvRealComoArchivoDeTexto', async ({ request }) => {
  const respuesta = await request.get('/api/estadisticas/exportar.csv')
  expect(respuesta.status()).toBe(200)
  expect(respuesta.headers()['content-type']).toMatch(/^text\/csv(?:;|$)/i)
})
