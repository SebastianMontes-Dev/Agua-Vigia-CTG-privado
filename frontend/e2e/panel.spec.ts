import { expect, test, type Page, type Route } from '@playwright/test'
import { problema, simularApi } from './api-simulada'

const JSON_OK = 'application/json'

function cuenta(sobrescribir: Record<string, unknown> = {}) {
  return {
    id: 'u-1', correo: 'ana@example.com', nombre: 'Ana Pérez', estado: 'ACTIVA', rol: 'VEEDOR',
    permisosEfectivos: ['VER_PANEL', 'MODERAR_REPORTES', 'GESTIONAR_CORTES', 'REVISAR_INGESTA', 'CONFIGURAR_SEGUNDO_FACTOR'],
    permisosConcedidos: [], permisosRevocados: [], segundoFactorActivo: false,
    creadoEn: '2026-09-01T12:00:00Z', actualizadoEn: '2026-09-01T12:00:00Z', ...sobrescribir,
  }
}

const reporte = (id: string, sectorId: string, tipo: string, minutos: number) => ({
  id, sectorId, tipo, timestamp: new Date(Date.now() - minutos * 60_000).toISOString(), estadoModeracion: 'PENDIENTE',
})

interface Opciones {
  cuenta?: Record<string, unknown>
  sesion?: (route: Route) => Promise<void>
  yo?: (route: Route) => Promise<void>
}

/** La API del panel simulada: sesión, cuenta, cola de moderación y las decisiones que se toman sobre ella. */
async function simularPanel(page: Page, opciones: Opciones = {}) {
  await simularApi(page)
  const cola = [reporte('r-1', 'armenia', 'SIN_AGUA', 90), reporte('r-2', 'manga', 'PRESION_BAJA', 20)]
  const decisiones: string[] = []
  await page.route('**/api/veedor/sesion', opciones.sesion ?? ((route) => route.fulfill({
    status: 200, contentType: JSON_OK, body: JSON.stringify({ token: 'jwt-e2e', alcance: 'COMPLETO', permisos: [] }),
  })))
  await page.route('**/api/veedor/sesion/cierre', (route) => route.fulfill({ status: 204 }))
  await page.route('**/api/veedor/yo', opciones.yo ?? ((route) => route.fulfill({
    status: 200, contentType: JSON_OK, body: JSON.stringify(cuenta(opciones.cuenta)),
  })))
  await page.route(/\/api\/veedor\/reportes\/pendientes(?:\?|$)/, (route) => route.fulfill({
    status: 200, contentType: JSON_OK, body: JSON.stringify(cola),
    headers: { 'X-Total-Count': String(cola.length), 'X-Total-Pages': '1', 'X-Page': '0', 'X-Page-Size': '20' },
  }))
  await page.route(/\/api\/veedor\/reportes\/[^/]+\/(aprobar|descartar)$/, (route) => {
    const [, id, decision] = /reportes\/([^/]+)\/(aprobar|descartar)/.exec(route.request().url()) ?? []
    decisiones.push(`${decision}:${id}`)
    const indice = cola.findIndex((elemento) => elemento.id === id)
    if (indice >= 0) cola.splice(indice, 1)
    return route.fulfill({ status: 200, contentType: JSON_OK, body: '{}' })
  })
  return { decisiones }
}

async function ingresar(page: Page) {
  await page.goto('/panel/ingreso')
  await page.getByRole('textbox', { name: 'Correo' }).fill('ana@example.com')
  await page.getByLabel('Clave', { exact: true }).fill('una clave larga y unica')
  await page.getByRole('button', { name: /^Ingresar/ }).click()
}

test('debeIngresarUnVeedorYModerarUnReporteConConfirmacion', async ({ page }) => {
  const { decisiones } = await simularPanel(page)
  await ingresar(page)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('2 reportes esperan tu revisión')
  await expect(page.getByText('Armenia', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Descartar el reporte de Armenia' }).click()
  const dialogo = page.getByRole('alertdialog')
  await expect(dialogo).toContainText('Vas a descartar el reporte «sin agua» de Armenia')
  expect(decisiones).toEqual([])
  await dialogo.getByRole('button', { name: 'Descartar reporte' }).click()

  await expect(page.getByRole('status').filter({ hasText: 'Reporte descartado en Armenia.' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('1 reporte espera tu revisión')
  expect(decisiones).toEqual(['descartar:r-1'])
})

test('debePermitirCancelarLaConfirmacionSinEnviarNada', async ({ page }) => {
  const { decisiones } = await simularPanel(page)
  await ingresar(page)
  await page.getByRole('button', { name: 'Aprobar el reporte de Manga' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Cancelar' }).click()
  await expect(page.getByRole('alertdialog')).toBeHidden()
  expect(decisiones).toEqual([])
})

test('noDebeOfrecerModerarAUnaCuentaQueSoloPuedeVer', async ({ page }) => {
  await simularPanel(page, { cuenta: { rol: 'OBSERVADOR', permisosEfectivos: ['VER_PANEL', 'CONFIGURAR_SEGUNDO_FACTOR'] } })
  await ingresar(page)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('2 reportes esperan tu revisión')
  await expect(page.getByRole('button', { name: /^Aprobar el reporte/ })).toHaveCount(0)
  await expect(page.getByText('Tu cuenta puede ver la cola, pero no decidir.')).toBeVisible()
})

test('debePedirElCodigoDeLaAppSinTratarloComoClaveMala', async ({ page }) => {
  const cuerpos: unknown[] = []
  await simularPanel(page, {
    sesion: (route) => {
      cuerpos.push(route.request().postDataJSON())
      return cuerpos.length === 1
        ? route.fulfill(problema(401, 'segundo-factor-requerido', 'Falta el código'))
        : route.fulfill({ status: 200, contentType: JSON_OK, body: JSON.stringify({ token: 'jwt-e2e', alcance: 'COMPLETO', permisos: [] }) })
    },
  })
  await ingresar(page)
  const codigo = page.getByRole('textbox', { name: 'Código de tu app de autenticación' })
  await expect(codigo).toBeFocused()
  await expect(page.getByRole('status')).toContainText('Escribe el código de seis dígitos')
  await codigo.fill('123456')
  await page.getByRole('button', { name: /^Ingresar/ }).click()
  await expect(page).toHaveURL(/\/panel$/)
  expect(cuerpos[1]).toMatchObject({ correo: 'ana@example.com', codigoTotp: '123456' })
})

test('noDebeDistinguirSiFalloElCorreoOLaClave', async ({ page }) => {
  await simularPanel(page, { sesion: (route) => route.fulfill(problema(401, 'credencial-invalida', 'Credenciales inválidas')) })
  await ingresar(page)
  await expect(page.getByRole('alert')).toHaveText('El correo o la clave no son correctos.')
  await expect(page).toHaveURL(/\/panel\/ingreso/)
})

test('debeExplicarLoQueOcurreConUnaCuentaSinAcceso', async ({ page }) => {
  await simularPanel(page, {
    sesion: (route) => route.fulfill({
      status: 403, contentType: 'application/problem+json',
      body: JSON.stringify({ type: 'https://aguavigia.example/errores/cuenta-no-habilitada', status: 403, detail: 'x', estado: 'PENDIENTE_APROBACION' }),
    }),
  })
  await ingresar(page)
  await expect(page.getByRole('alert')).toContainText('espera la aprobación de un administrador')
})

test('debeLlevarAlPrimerIngresoDeUnAdminAActivarSuSegundoFactor', async ({ page }) => {
  const confirmaciones: unknown[] = []
  await simularPanel(page, {
    sesion: (route) => route.fulfill({ status: 200, contentType: JSON_OK, body: JSON.stringify({ token: 'jwt-alta', alcance: 'ALTA_SEGUNDO_FACTOR', permisos: [] }) }),
    cuenta: { rol: 'ADMIN', segundoFactorActivo: true },
  })
  await page.route('**/api/veedor/segundo-factor/alta', (route) => route.fulfill({
    status: 200, contentType: JSON_OK,
    body: JSON.stringify({ uri: 'otpauth://totp/AguaVigia:ana@example.com?secret=JBSWY3DPEHPK3PXP&issuer=AguaVigia', secreto: 'JBSWY3DPEHPK3PXP' }),
  }))
  await page.route('**/api/veedor/segundo-factor/confirmacion', (route) => {
    confirmaciones.push(route.request().postDataJSON())
    return route.fulfill({ status: 200, contentType: JSON_OK, body: JSON.stringify({ token: 'jwt-completo', alcance: 'COMPLETO', permisos: [] }) })
  })
  await ingresar(page)
  await expect(page).toHaveURL(/\/panel\/segundo-factor/)
  await expect(page.getByRole('img', { name: 'Código QR para tu app de autenticación' })).toHaveAttribute('src', /^data:image\/svg\+xml/)
  await expect(page.getByText('JBSWY3DPEHPK3PXP')).toBeVisible()
  await page.getByRole('textbox', { name: 'Código de seis dígitos' }).fill('654321')
  await page.getByRole('button', { name: /^Activar segundo factor/ }).click()
  await expect(page).toHaveURL(/\/panel$/)
  expect(confirmaciones).toEqual([{ codigo: '654321' }])
  expect(await page.evaluate(() => sessionStorage.getItem('aguavigia.panel.token'))).toBe('jwt-completo')
})

test('noDebeAbrirElPanelConUnaSesionDeAlta', async ({ page }) => {
  await simularPanel(page)
  await page.goto('/panel/ingreso')
  await page.evaluate(() => {
    sessionStorage.setItem('aguavigia.panel.token', 'jwt-alta')
    sessionStorage.setItem('aguavigia.panel.alcance', 'ALTA_SEGUNDO_FACTOR')
  })
  await page.goto('/panel')
  await expect(page).toHaveURL(/\/panel\/segundo-factor/)
})

test('debeVolverAlIngresoSinSesion', async ({ page }) => {
  await simularPanel(page)
  await page.goto('/panel')
  await expect(page).toHaveURL(/\/panel\/ingreso/)
})

test('debeAvisarQueLaSesionTerminoCuandoElServidorLaRechaza', async ({ page }) => {
  await simularPanel(page, { yo: (route) => route.fulfill(problema(401, 'sesion-sin-cuenta', 'Sesión revocada')) })
  await page.goto('/panel/ingreso')
  await page.evaluate(() => sessionStorage.setItem('aguavigia.panel.token', 'jwt-viejo'))
  await page.goto('/panel')
  await expect(page).toHaveURL(/\/panel\/ingreso\?motivo=terminada/)
  await expect(page.getByRole('status')).toHaveText('Tu sesión terminó. Ingresa de nuevo.')
  expect(await page.evaluate(() => sessionStorage.getItem('aguavigia.panel.token'))).toBeNull()
})

test('debeCerrarLaSesionYNoDejarNadaGuardado', async ({ page }) => {
  await simularPanel(page)
  await ingresar(page)
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await page.goto('/panel/seguridad')
  await page.getByRole('button', { name: 'Cerrar todas mis sesiones' }).click()
  await expect(page).toHaveURL(/\/panel\/ingreso$/)
  expect(await page.evaluate(() => sessionStorage.getItem('aguavigia.panel.token'))).toBeNull()
})

test('debeCambiarLaClaveYPedirIngresarDeNuevo', async ({ page }) => {
  const cuerpos: unknown[] = []
  await simularPanel(page)
  await page.route('**/api/veedor/cuenta/clave', (route) => {
    cuerpos.push(route.request().postDataJSON())
    return route.fulfill({ status: 204 })
  })
  await ingresar(page)
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await page.goto('/panel/seguridad')
  await page.getByLabel('Clave de hoy').fill('una clave larga y unica')
  await page.getByLabel('Clave nueva').fill('corta')
  await page.getByRole('button', { name: /^Guardar clave nueva/ }).click()
  await expect(page.getByRole('alert')).toContainText('al menos 12 caracteres')
  expect(cuerpos).toEqual([])
  await page.getByLabel('Clave nueva').fill('otra clave larga y distinta')
  await page.getByRole('button', { name: /^Guardar clave nueva/ }).click()
  await expect(page).toHaveURL(/\/panel\/ingreso\?motivo=clave-cambiada/)
  await expect(page.getByRole('status')).toContainText('Cambiaste tu clave y cerramos tus sesiones')
  expect(cuerpos).toEqual([{ claveActual: 'una clave larga y unica', claveNueva: 'otra clave larga y distinta' }])
})

test('debeResponderIgualExistaONoLaCuentaAlRecuperarLaClave', async ({ page }) => {
  await simularApi(page)
  await page.route('**/api/cuentas/restablecimiento', (route) => route.fulfill({ status: 202 }))
  await page.goto('/cuenta/olvide')
  await page.getByRole('textbox', { name: 'Correo' }).fill('nadie@example.com')
  await page.getByRole('button', { name: /^Enviar enlace/ }).click()
  await expect(page.getByRole('status')).toContainText('Si hay una cuenta con ese correo, te enviamos un enlace')
})

test('debeAceptarUnaInvitacionRetirandoElTokenDeLaUrl', async ({ page }) => {
  await simularApi(page)
  const cuerpos: unknown[] = []
  await page.route('**/api/cuentas/invitacion', (route) => {
    cuerpos.push(route.request().postDataJSON())
    return route.fulfill({ status: 204 })
  })
  await page.goto('/cuenta/invitacion?token=tok-secreto')
  await expect(page).not.toHaveURL(/token=/)
  await page.getByLabel('Clave', { exact: true }).fill('una clave larga y unica')
  await page.getByRole('button', { name: /^Aceptar invitación/ }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Tu cuenta está activa')
  expect(cuerpos).toEqual([{ token: 'tok-secreto', clave: 'una clave larga y unica' }])
})

test('debeOfrecerPedirOtroEnlaceCuandoElDeRestablecerYaNoSirve', async ({ page }) => {
  await simularApi(page)
  await page.route('**/api/cuentas/clave', (route) => route.fulfill(problema(409, 'conflicto-de-estado', 'Token usado')))
  await page.goto('/cuenta/restablecer?token=tok-viejo')
  await page.getByLabel('Clave nueva').fill('una clave larga y unica')
  await page.getByRole('button', { name: /^Guardar clave/ }).click()
  await expect(page.getByRole('heading', { name: 'Este enlace ya se usó o venció' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Pedir otro enlace' })).toBeVisible()
})

test('nadaDeLaVerificacionDebeEjecutarseAlAbrirElEnlace', async ({ page }) => {
  await simularApi(page)
  let llamadas = 0
  await page.route('**/api/cuentas/verificacion**', (route) => { llamadas++; return route.fulfill({ status: 204 }) })
  await page.goto('/cuenta/verificar?token=tok-verif')
  await expect(page.getByRole('button', { name: /^Verificar mi correo/ })).toBeVisible()
  expect(llamadas).toBe(0)
  await page.getByRole('button', { name: /^Verificar mi correo/ }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Correo verificado')
  expect(llamadas).toBe(1)
})
