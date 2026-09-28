import { randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'

function huella(): string {
  return randomUUID().replaceAll('-', '') + randomUUID().replaceAll('-', '')
}

async function abrirReporte(page: Page, opcion: string) {
  await page.getByRole('button', { name: 'Reportar lo que pasa en mi casa' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('button', { name: opcion, exact: true }).click()
}

test('debeAbrirElMapaConLos211BarriosYElCanalEnVivoSinRecursosExternos', async ({ page }) => {
  const externos: string[] = []
  page.on('request', (peticion) => {
    if (peticion.url().startsWith('http') && new URL(peticion.url()).origin !== 'http://localhost:4173') {
      externos.push(peticion.url())
    }
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1, name: 'Cartagena ahora' })).toBeVisible()
  await page.getByRole('button', { name: 'Ver la lista de los 211 barrios con su estado' }).click()
  const lista = page.getByRole('region', { name: 'Todos los barrios' })
  await expect(lista.getByRole('link')).toHaveCount(211)
  await expect(page.getByText('En vivo', { exact: true })).toBeVisible()

  await page.getByRole('combobox', { name: 'Busca tu barrio' }).fill('zona industrial')
  await page.getByRole('option', { name: /Zona Industrial/i }).click()
  await expect(page).toHaveURL(/\/sectores\/zona-industrial$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Zona Industrial' })).toBeVisible()
  expect(externos).toEqual([])
})

test('debeReportarEnDosToquesYRecibirUn201', async ({ page }) => {
  await page.goto('/sectores/zona-industrial')
  await expect(page.getByRole('heading', { level: 1, name: 'Zona Industrial' })).toBeVisible()

  const respuesta = page.waitForResponse((valor) => valor.url().endsWith('/api/reportes') && valor.request().method() === 'POST')
  await abrirReporte(page, 'No tengo agua')

  expect((await respuesta).status()).toBe(201)
  await expect(page.getByRole('dialog').getByRole('heading', { name: 'Reporte recibido' })).toBeVisible()
  await expect(page.getByRole('dialog')).toContainText('Tu reporte cuenta junto con los de tus vecinos')
})

test('debeMostrarElMensajeDel429EnElCuartoReporteDeLaMismaHuella', async ({ page }) => {
  const huellas: string[] = []
  page.on('request', (peticion) => {
    if (peticion.method() === 'POST' && new URL(peticion.url()).pathname === '/api/reportes') {
      huellas.push((peticion.postDataJSON() as { huella: string }).huella)
    }
  })
  await page.goto('/sectores/alameda-la-victoria')
  await expect(page.getByRole('heading', { level: 1, name: 'Alameda la Victoria' })).toBeVisible()

  for (let intento = 0; intento < 4; intento++) {
    const respuesta = page.waitForResponse((valor) => valor.url().endsWith('/api/reportes') && valor.request().method() === 'POST')
    await abrirReporte(page, 'Llega poca agua')
    expect((await respuesta).status()).toBe(intento < 3 ? 201 : 429)

    if (intento < 3) {
      await expect(page.getByRole('heading', { name: 'Reporte recibido' })).toBeVisible()
      await page.getByRole('button', { name: 'Volver a Alameda la Victoria' }).click()
    } else {
      await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Ya enviaste tres reportes de este barrio')
    }
  }
  expect(huellas).toHaveLength(4)
  expect(new Set(huellas).size).toBe(1)
})

test('debeActualizarElEstadoPorConsensoSSESinRecargarLaPagina', async ({ page, request }) => {
  await page.goto('/sectores/arroyo-grande')
  const ficha = page.getByRole('article', { name: 'Arroyo Grande' })
  await expect(ficha).toBeVisible()
  await expect(page.getByText('En vivo', { exact: true })).toBeVisible()

  const yaSinServicio = await ficha.getByText('Sin servicio', { exact: true }).isVisible()
  const tipo = yaSinServicio ? 'SERVICIO_RESTABLECIDO' : 'SIN_AGUA'
  const estadoEsperado = yaSinServicio ? 'Con servicio' : 'Sin servicio'

  for (let vecino = 0; vecino < 3; vecino++) {
    const respuesta = await request.post('/api/reportes', {
      data: { sectorId: 'arroyo-grande', tipo, huella: huella() },
    })
    expect(respuesta.status()).toBe(201)
  }

  await expect(ficha.getByText(estadoEsperado, { exact: true })).toBeVisible({ timeout: 30_000 })
})

test('debeConfirmarSoloAlTocarUnaVezEInformarSiElReporteNoExiste', async ({ page, request }) => {
  const creado = await request.post('/api/reportes', {
    data: { sectorId: 'manga', tipo: 'PRESION_BAJA', huella: huella() },
  })
  expect(creado.status()).toBe(201)
  const reporte = await creado.json() as { id: string }
  let confirmaciones = 0
  page.on('request', (peticion) => {
    if (peticion.method() === 'POST' && new URL(peticion.url()).pathname === `/api/reportes/${reporte.id}/confirmar`) {
      confirmaciones++
    }
  })

  await page.goto(`/confirmar/${reporte.id}`)
  await expect(page.getByRole('heading', { level: 1, name: 'Confirmar reporte' })).toBeVisible()
  await page.waitForTimeout(500)
  expect(confirmaciones).toBe(0)

  const respuesta = page.waitForResponse((valor) => valor.url().endsWith(`/api/reportes/${reporte.id}/confirmar`))
  await page.getByRole('button', { name: 'Confirmar este reporte' }).click()
  expect((await respuesta).status()).toBe(200)
  await expect(page.getByRole('heading', { name: 'Confirmación recibida' })).toBeVisible()
  expect(confirmaciones).toBe(1)

  await page.goto('/confirmar/no-existe')
  const inexistente = page.waitForResponse((valor) => valor.url().endsWith('/api/reportes/no-existe/confirmar'))
  await page.getByRole('button', { name: 'Confirmar este reporte' }).click()
  expect((await inexistente).status()).toBe(404)
  await expect(page.getByRole('alert')).toContainText('Este reporte no está disponible')
})
