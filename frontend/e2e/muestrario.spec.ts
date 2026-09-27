import { expect, test, type Page } from '@playwright/test'

// toHaveCSS reintenta: el cambio de tema dura 240 ms (identidad.md §5) y una lectura inmediata ve el color de antes.
function esperarFondo(page: Page, color: string) {
  return expect(page.locator('body')).toHaveCSS('background-color', color)
}

const PAPEL_CLARO = 'rgb(245, 245, 242)'
const PAPEL_OSCURO = 'rgb(15, 18, 20)'

test('debeAplicarLosTokensEnElTemaClaroYEnElOscuro', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/muestrario')
  await expect(page.getByRole('heading', { level: 1, name: 'Identidad del frontend' })).toBeVisible()
  await esperarFondo(page, PAPEL_CLARO)

  await page.getByRole('radio', { name: 'Oscuro' }).check()
  await esperarFondo(page, PAPEL_OSCURO)

  await page.reload()
  await esperarFondo(page, PAPEL_OSCURO)
})

test('debeSeguirAlSistemaYDejarQueElInterruptorLoContradiga', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.goto('/muestrario')
  await esperarFondo(page, PAPEL_OSCURO)

  await page.getByRole('radio', { name: 'Claro' }).check()
  await esperarFondo(page, PAPEL_CLARO)

  await page.reload()
  await esperarFondo(page, PAPEL_CLARO)
  await expect(page.getByRole('radio', { name: 'Claro' })).toBeChecked()
})

// Cifras de identidad.md §2; el botón principal lleva --sobre-cardenillo sobre --cardenillo.
const ACCION = {
  light: {
    color: 'rgb(47, 95, 87)',
    razones: { papel: '6,64:1', superficie: '7,00:1', suave: '6,02:1', boton: '7,25:1', laton: '5,02:1' },
  },
  dark: {
    color: 'rgb(140, 194, 180)',
    razones: { papel: '9,39:1', superficie: '8,83:1', suave: '7,28:1', boton: '9,39:1', laton: '8,11:1' },
  },
} as const

const REGION_ACCION = 'Acción y combinaciones permitidas'

for (const [esquema, esperado] of Object.entries(ACCION)) {
  test(`debeMostrarLaAccionYSusCombinacionesEnElTema ${esquema}`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: esquema as 'light' | 'dark' })
    await page.goto('/muestrario')
    const seccion = page.getByRole('region', { name: REGION_ACCION })
    const filas = seccion.getByRole('listitem')

    await expect(filas.nth(0)).toContainText(`Cardenillo sobre papel${esperado.razones.papel}`)
    await expect(filas.nth(1)).toContainText(`Cardenillo sobre superficie${esperado.razones.superficie}`)
    await expect(filas.nth(2)).toContainText(`Cardenillo sobre cardenillo suave${esperado.razones.suave}`)
    await expect(filas.nth(3)).toContainText(`Botón principal${esperado.razones.boton}`)
    await expect(filas.nth(4)).toContainText(`Latón: foco y selección${esperado.razones.laton}`)
    await expect(seccion.getByText('Cardenillo sobre papel')).toHaveCSS('color', esperado.color)

    const desborda = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    )
    expect(desborda).toBe(false)
  })
}

test('debeMedirLaAccionDelTemaElegidoAMano', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/muestrario')
  const fila = page.getByRole('region', { name: REGION_ACCION }).getByRole('listitem').first()

  await page.getByRole('radio', { name: 'Oscuro' }).check()
  await expect(fila).toContainText('9,39:1')

  await page.emulateMedia({ colorScheme: 'light' })
  await expect(fila).toContainText('9,39:1')

  await page.getByRole('radio', { name: 'Claro' }).check()
  await expect(fila).toContainText('6,64:1')
})

test('debeVolverAMedirLaAccionCuandoCambiaElSistemaSinPreferenciaManual', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/muestrario')
  const fila = page.getByRole('region', { name: REGION_ACCION }).getByRole('listitem').first()
  await expect(fila).toContainText('6,64:1')

  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(fila).toContainText('9,39:1')
})

// ADR-071: la serif llega del mismo origen y solo en la marca y los titulares; los controles usan la del sistema.
test('debeServirNewsreaderDesdeElProyectoYSoloEnMarcaYTitular', async ({ page }) => {
  const pedidas: string[] = []
  page.on('request', (peticion) => pedidas.push(peticion.url()))
  await page.goto('/muestrario')
  await expect(page.getByRole('heading', { level: 1 })).toHaveCSS('font-family', /^Newsreader/)
  await expect(page.getByRole('radio', { name: 'Claro' })).not.toHaveCSS('font-family', /Newsreader/)
  await page.evaluate(() => document.fonts.ready)
  expect(pedidas.filter((url) => url.includes('.woff2'))).toEqual([
    expect.stringMatching(/\/fuentes\/newsreader-latin-wght\.woff2$/),
  ])
  expect(pedidas.filter((url) => !url.startsWith(new URL(page.url()).origin))).toEqual([])
})

test('noDebeHacerScrollHorizontalEnElCuerpo', async ({ page }) => {
  await page.goto('/muestrario')
  const desborda = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  )
  expect(desborda).toBe(false)
})
