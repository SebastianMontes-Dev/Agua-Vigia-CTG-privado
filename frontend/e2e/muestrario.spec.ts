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

  await page.reload()
  expect(await fondoDelCuerpo(page)).toBe('rgb(242, 247, 246)')
  await expect(page.getByRole('radio', { name: 'Claro' })).toBeChecked()
})

// Cifras de guia-frontend.md §2.3; el botón principal lleva --superficie en claro y --fondo en oscuro.
const ACENTO = {
  light: {
    color: 'rgb(6, 116, 127)',
    razones: { fondo: '5,09:1', superficie: '5,39:1', 'acento suave': '4,62:1', boton: '5,39:1' },
  },
  dark: {
    color: 'rgb(84, 198, 202)',
    razones: { fondo: '8,60:1', superficie: '7,57:1', 'acento suave': '5,64:1', boton: '8,60:1' },
  },
} as const

for (const [esquema, esperado] of Object.entries(ACENTO)) {
  test(`debeMostrarElAcentoYSusCombinacionesEnElTema ${esquema}`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: esquema as 'light' | 'dark' })
    await page.goto('/')
    const seccion = page.getByRole('region', { name: 'Acento y combinaciones permitidas' })
    const filas = seccion.getByRole('listitem')

    await expect(filas.nth(0)).toContainText(`Acento sobre fondo${esperado.razones.fondo}`)
    await expect(filas.nth(1)).toContainText(`Acento sobre superficie${esperado.razones.superficie}`)
    await expect(filas.nth(2)).toContainText(`Acento sobre acento suave${esperado.razones['acento suave']}`)
    await expect(filas.nth(3)).toContainText(`Botón principal${esperado.razones.boton}`)
    await expect(seccion.getByText('Acento sobre fondo')).toHaveCSS('color', esperado.color)

    const desborda = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    )
    expect(desborda).toBe(false)
  })
}

test('debeMedirElAcentoDelTemaElegidoAMano', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/')
  const fila = page.getByRole('region', { name: 'Acento y combinaciones permitidas' }).getByRole('listitem').first()

  await page.getByRole('radio', { name: 'Oscuro' }).check()
  await expect(fila).toContainText('8,60:1')

  await page.emulateMedia({ colorScheme: 'light' })
  await expect(fila).toContainText('8,60:1')

  await page.getByRole('radio', { name: 'Claro' }).check()
  await expect(fila).toContainText('5,09:1')
})

test('debeVolverAMedirElAcentoCuandoCambiaElSistemaSinPreferenciaManual', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/')
  const fila = page.getByRole('region', { name: 'Acento y combinaciones permitidas' }).getByRole('listitem').first()
  await expect(fila).toContainText('5,09:1')

  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(fila).toContainText('8,60:1')
})

test('noDebeHacerScrollHorizontalEnElCuerpo', async ({ page }) => {
  await page.goto('/')
  const desborda = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  )
  expect(desborda).toBe(false)
})
