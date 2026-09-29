import { randomUUID } from 'node:crypto'
import { expect, test, type APIRequestContext } from '@playwright/test'

interface MensajeMailhog {
  To: { Mailbox: string; Domain: string }[]
  Content: { Body: string; Headers?: Record<string, string[]> }
  MIME?: { Parts?: { Body: string; Headers?: Record<string, string[]> }[] }
}

async function correoDe(request: APIRequestContext, correo: string): Promise<string> {
  for (let intento = 0; intento < 30; intento++) {
    const respuesta = await request.get(`${process.env.MAILHOG_URL ?? 'http://localhost:8025'}/api/v2/messages`)
    expect(respuesta.ok()).toBeTruthy()
    const datos = await respuesta.json() as { items: MensajeMailhog[] }
    const mensaje = datos.items.find((item) => item.To.some((destino) => `${destino.Mailbox}@${destino.Domain}` === correo))
    if (mensaje) {
      const cuerpo = [mensaje.Content.Body, ...(mensaje.MIME?.Parts?.map((parte) => parte.Body) ?? [])].join('\n')
      return cuerpo.replace(/=\r?\n/g, '').replace(/=3D/gi, '=').replace(/&amp;/g, '&')
    }
    await new Promise((resolver) => setTimeout(resolver, 500))
  }
  throw new Error(`Mailhog no recibió el correo para ${correo}`)
}

function rutaEnCorreo(cuerpo: string, accion: 'confirmar' | 'baja'): string {
  const coincidencia = cuerpo.match(new RegExp(`https?://[^"'\\s<>]+/avisos/${accion}\\?token=[^"'\\s<>]+`))
  if (!coincidencia) throw new Error(`Falta el enlace de ${accion} en el correo`)
  const enlace = new URL(coincidencia[0])
  return `${enlace.pathname}${enlace.search}`
}

test('debeSuscribirseConfirmarYDarseDeBajaSoloDespuesDePulsarCadaBoton', async ({ page, request }) => {
  const correo = `avisos-${randomUUID()}@example.com`
  const acciones: string[] = []
  page.on('request', (peticion) => {
    if (peticion.method() === 'POST' && /\/api\/suscripciones\/(confirmar|cancelar)/.test(peticion.url())) acciones.push(peticion.url())
  })

  await page.goto('/avisos?sector=manga')
  await expect(page.getByRole('list', { name: 'Barrios elegidos' })).toContainText('Manga')
  await page.getByRole('textbox', { name: 'Tu correo' }).fill(correo)
  const alta = page.waitForResponse((respuesta) => respuesta.url().endsWith('/api/suscripciones') && respuesta.request().method() === 'POST')
  await page.getByRole('button', { name: /Enviar enlace de confirmación/ }).click()
  expect((await alta).status()).toBe(201)
  await expect(page.getByRole('status')).toContainText('Si la dirección es válida')

  const cuerpo = await correoDe(request, correo)
  const confirmar = rutaEnCorreo(cuerpo, 'confirmar')
  const baja = rutaEnCorreo(cuerpo, 'baja')

  await page.goto(confirmar)
  await expect(page).toHaveURL(/\/avisos\/confirmar$/)
  expect(acciones).toHaveLength(0)
  const confirmacion = page.waitForResponse((respuesta) => respuesta.url().includes('/api/suscripciones/confirmar?token='))
  await page.getByRole('button', { name: 'Confirmar avisos' }).click()
  expect((await confirmacion).status()).toBe(200)
  await expect(page.getByRole('status')).toContainText('Avisos confirmados')

  await page.goto(baja)
  await expect(page).toHaveURL(/\/avisos\/baja$/)
  expect(acciones).toHaveLength(1)
  const cancelacion = page.waitForResponse((respuesta) => respuesta.url().includes('/api/suscripciones/cancelar?token='))
  await page.getByRole('button', { name: 'Dejar de recibir avisos' }).click()
  expect((await cancelacion).status()).toBe(200)
  await expect(page.getByRole('status')).toContainText('Ya no recibirás estos avisos')
  expect(acciones).toHaveLength(2)
})
