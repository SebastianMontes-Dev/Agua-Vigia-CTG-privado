#!/usr/bin/env node
/**
 * Recorre con peticiones HTTP reales los flujos que un frontend va a consumir (Fase 5 de
 * `docs/ingenieria/plan-validacion-backend.md`) y dice cuáles funcionan. No es una prueba unitaria:
 * necesita el entorno levantado (`docker compose up -d`), los sectores sembrados y un ADMIN sin
 * segundo factor todavía (base recién creada).
 *
 *   node scripts/verificar-flujos.mjs
 *
 * Variables (todas opcionales; los valores por defecto son los del compose local):
 *   API_URL            http://localhost:8081
 *   MAILHOG_URL        http://localhost:8025
 *   ORIGEN_FRONTEND    http://localhost:5173   (se comprueba que CORS lo deje pasar)
 *   ADMIN_CORREO       veedor@aguavigia.local
 *   ADMIN_CLAVE        la clave cuyo hash pusiste en VEEDOR_PASSWORD_HASH
 *   TOTP_SECRETO       solo si el ADMIN ya dio de alta su segundo factor (el que mostró el alta)
 *
 * Deja datos de prueba en la base (un reporte por dispositivo simulado, una suscripción, un corte
 * y una cuenta invitada): úsalo en un entorno de desarrollo, no en uno con datos que importen.
 *
 * Cada reporte lleva su identidad (ADR-090): un token de dispositivo propio (POST /api/dispositivos, 10 por hora por IP; este
 * script usa 8 por pasada, así que para repetirlo seguido levanta el backend con RATE_LIMIT_FACTOR=100) y una coordenada dentro
 * del barrio con precisión de 10 m, que es lo que el quórum exige para contar un voto como verificado. Con redes-minimas=2, el
 * valor por defecto del backend, un solo equipo no alcanza el quórum: el compose local arranca con 1.
 */
import { randomBytes } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { parse } from 'yaml'
import { codigoTotp } from './codigo-totp.mjs'
import { crearDispositivo, puntosPorBarrio } from './lib/identidad-api.mjs'
import { iniciarSesionAdmin } from './lib/sesion-admin.mjs'

const API = (process.env.API_URL ?? 'http://localhost:8081').replace(/\/$/, '')
const MAILHOG = (process.env.MAILHOG_URL ?? 'http://localhost:8025').replace(/\/$/, '')
const ORIGEN = process.env.ORIGEN_FRONTEND ?? 'http://localhost:5173'
const ADMIN_CORREO = process.env.ADMIN_CORREO ?? 'veedor@aguavigia.local'
const ADMIN_CLAVE = process.env.ADMIN_CLAVE
const SUFIJO = randomBytes(3).toString('hex')

const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

let pasaron = 0
const fallaron = []

// Registro de cobertura (docs/reduccion/R0): cada petición se asocia a su «MÉTODO /plantilla» del contrato; al final se listan
// las operaciones de backend/openapi.yaml que nadie recorrió. /api/sim/** lo cubre el guion de simulación (backend-sim).
const PLANTILLAS = Object.entries(parse(readFileSync(new URL('../backend/openapi.yaml', import.meta.url), 'utf8')).paths).flatMap(
  ([ruta, operaciones]) =>
    ['get', 'post', 'put', 'patch', 'delete']
      .filter((m) => operaciones[m])
      .map((m) => ({
        clave: `${m.toUpperCase()} ${ruta}`,
        metodo: m.toUpperCase(),
        regex: new RegExp(`^${ruta.replace(/\{[^}]+\}/g, '[^/]+')}$`),
      })),
)
const recorridas = new Set()
function registrarCobertura(metodo, ruta) {
  const limpia = ruta.split('?')[0]
  // La plantilla más específica gana (menos llaves): /api/veedor/cortes/vencidos antes que /api/veedor/cortes/{id}.
  const candidatas = PLANTILLAS.filter((p) => p.metodo === metodo && p.regex.test(limpia))
  candidatas.sort((a, b) => a.clave.split('{').length - b.clave.split('{').length)
  if (candidatas[0]) recorridas.add(candidatas[0].clave)
}

async function paso(nombre, fn) {
  try {
    const detalle = await fn()
    pasaron++
    console.log(`  ✔ ${nombre}${detalle ? ` — ${detalle}` : ''}`)
  } catch (error) {
    fallaron.push(nombre)
    console.log(`  ✘ ${nombre}\n      ${String(error.message).split('\n').join('\n      ')}`)
  }
}

function exigir(condicion, mensaje) {
  if (!condicion) throw new Error(mensaje)
}

async function http(metodo, ruta, { cuerpo, token, cabeceras = {}, formulario } = {}) {
  const init = { method: metodo, headers: { Accept: 'application/json', ...cabeceras } }
  if (token) init.headers.Authorization = `Bearer ${token}`
  if (formulario) {
    init.body = formulario
  } else if (cuerpo !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(cuerpo)
  }
  registrarCobertura(metodo, ruta)
  const respuesta = await fetch(`${API}${ruta}`, init)
  const texto = await respuesta.text()
  let json = null
  try {
    json = texto ? JSON.parse(texto) : null
  } catch {
    /* HTML u otro formato */
  }
  return { estado: respuesta.status, json, texto, cabeceras: respuesta.headers }
}

/**
 * El token del correo más reciente para `destinatario` que traiga el enlace `fragmentoDeRuta?token=` y no esté en `excluir`.
 * Un reenvío o una nueva petición invalida los enlaces anteriores, así que hay que usar siempre el último.
 */
async function esperarToken(destinatario, fragmentoDeRuta, { excluir = [], intentos = 30 } = {}) {
  for (let i = 0; i < intentos; i++) {
    for (const correo of await correosPara(destinatario, { intentos: 1 })) {
      const coincidencia = new RegExp(`${fragmentoDeRuta}\\?token=([A-Za-z0-9_.\\-]+)`).exec(correo.cuerpo)
      if (coincidencia && !excluir.includes(coincidencia[1])) return coincidencia[1]
    }
    await new Promise((ok) => setTimeout(ok, 400))
  }
  throw new Error(`no llegó a ${destinatario} un correo con el enlace «${fragmentoDeRuta}»`)
}

const esperarSiguienteFranjaTotp = () => new Promise((ok) => setTimeout(ok, 30_500 - (Date.now() % 30_000)))

/** Un token de dispositivo nuevo (POST /api/dispositivos); lo cuenta para la cobertura aunque lo haga la librería compartida. */
async function nuevoDispositivo() {
  registrarCobertura('POST', '/api/dispositivos')
  return crearDispositivo(API)
}

function esperarEstado(respuesta, esperado, contexto) {
  exigir(
    respuesta.estado === esperado,
    `${contexto}: se esperaba ${esperado} y llegó ${respuesta.estado} ${respuesta.texto.slice(0, 200)}`,
  )
  return respuesta
}

async function correosPara(destinatario, { intentos = 20 } = {}) {
  for (let i = 0; i < intentos; i++) {
    const r = await fetch(`${MAILHOG}/api/v2/search?kind=to&query=${encodeURIComponent(destinatario)}`)
    const { items } = await r.json()
    if (items.length) return items.map((c) => ({
      asunto: c.Content.Headers.Subject?.[0] ?? '',
      cuerpo: c.Content.Body.replace(/=\r?\n/g, '').replace(/=3D/g, '='),
    }))
    await new Promise((ok) => setTimeout(ok, 500))
  }
  return []
}

function tokenDeEnlace(cuerpo, fragmentoDeRuta) {
  const coincidencia = new RegExp(`${fragmentoDeRuta}\\?token=([A-Za-z0-9_.\\-]+)`).exec(cuerpo)
  exigir(coincidencia, `no hay un enlace «${fragmentoDeRuta}» en el correo`)
  return coincidencia[1]
}

async function escucharSse() {
  const control = new AbortController()
  const eventos = []
  registrarCobertura('GET', '/api/sectores/stream')
  const respuesta = await fetch(`${API}/api/sectores/stream`, {
    headers: { Accept: 'text/event-stream' },
    signal: control.signal,
  })
  exigir(respuesta.status === 200, `el stream respondió ${respuesta.status}`)
  exigir(
    (respuesta.headers.get('content-type') ?? '').includes('text/event-stream'),
    'el stream no es text/event-stream',
  )
  ;(async () => {
    const lector = respuesta.body.getReader()
    const decodificador = new TextDecoder()
    try {
      for (;;) {
        const { done, value } = await lector.read()
        if (done) break
        eventos.push(decodificador.decode(value))
      }
    } catch {
      /* abortado al terminar */
    }
  })()
  return { eventos, cerrar: () => control.abort() }
}

console.log(`\nVerificando ${API}\n`)

// ---------------------------------------------------------------------------------------------
console.log('Ciudadano anónimo')
let sectores = []
let sector

await paso('la API responde lista (readiness)', async () => {
  const r = await http('GET', '/actuator/health/readiness')
  exigir(r.estado === 200 && r.json?.status === 'UP', `readiness ${r.estado}`)
})

await paso('GET /api/sectores devuelve los sectores sembrados', async () => {
  const r = esperarEstado(await http('GET', '/api/sectores'), 200, 'listar sectores')
  sectores = r.json.sectores
  exigir(sectores.length > 0, 'no hay sectores: falta correr scripts/sembrar-sectores.mjs')
  // Un sector sin estado todavía (para que el consenso produzca un cambio y salgan aviso y evento) y el de
  // menor población, que tiene el umbral de consenso más bajo (mínimo 3). Al repetir el script se usa otro.
  const porPoblacion = [...sectores].filter((s) => s.poblacion > 0).sort((a, b) => a.poblacion - b.poblacion)
  sector = porPoblacion.find((s) => s.estado === null) ?? porPoblacion[0] ?? sectores[0]
  return `${sectores.length} sectores; se usará «${sector.id}»`
})

await paso('GET /api/sectores/geometria trae un Feature por sector', async () => {
  const r = esperarEstado(await http('GET', '/api/sectores/geometria', { cabeceras: { Accept: 'application/geo+json' } }), 200, 'geometría')
  exigir(r.json.features.length === sectores.length, `${r.json.features.length} features y ${sectores.length} sectores`)
  exigir((r.cabeceras.get('cache-control') ?? '').includes('max-age'), 'sin Cache-Control')
})

await paso('GET /api/sectores/{id} y 404 con problem+json', async () => {
  esperarEstado(await http('GET', `/api/sectores/${sector.id}`), 200, 'sector')
  const r = esperarEstado(await http('GET', '/api/sectores/no-existe-xyz'), 404, 'sector inexistente')
  exigir((r.cabeceras.get('content-type') ?? '').includes('problem+json'), 'el error no es RFC 7807')
})

await paso(`CORS deja pasar a ${ORIGEN} y rechaza a un origen ajeno`, async () => {
  const preflight = (origen) =>
    fetch(`${API}/api/sectores`, {
      method: 'OPTIONS',
      headers: { Origin: origen, 'Access-Control-Request-Method': 'GET' },
    })
  const bueno = await preflight(ORIGEN)
  exigir(bueno.status === 200, `preflight desde ${ORIGEN}: ${bueno.status}`)
  exigir(bueno.headers.get('access-control-allow-origin') === ORIGEN, 'no devuelve Access-Control-Allow-Origin')
  const malo = await preflight('http://origen-ajeno.example')
  exigir(malo.status === 403, `un origen ajeno debería recibir 403 y recibió ${malo.status}`)
})

// ---------------------------------------------------------------------------------------------
console.log('\nSuscripción por correo (doble confirmación)')
const correoSuscriptor = `vecino-${SUFIJO}@example.com`
let tokenBaja

await paso('POST /api/suscripciones → 201 PENDIENTE_CONFIRMACION', async () => {
  const r = esperarEstado(
    await http('POST', '/api/suscripciones', { cuerpo: { correo: correoSuscriptor, sectorIds: [sector.id] } }),
    201,
    'suscribirse',
  )
  exigir(r.json.estado === 'PENDIENTE_CONFIRMACION', `estado ${r.json.estado}`)
  exigir(!('token' in r.json), 'el token no debe viajar en la respuesta')
})

await paso('el correo de confirmación llega a MailHog y confirma con POST', async () => {
  const correos = await correosPara(correoSuscriptor)
  exigir(correos.length > 0, 'no llegó ningún correo (¿MailHog en ' + MAILHOG + '?)')
  const token = tokenDeEnlace(correos[0].cuerpo, 'confirmar')
  const pagina = await http('GET', `/api/suscripciones/confirmar?token=${token}`, { cabeceras: { Accept: 'text/html' } })
  exigir(pagina.estado === 200 && pagina.texto.includes('<'), 'el GET no muestra la página con el botón')
  esperarEstado(await http('POST', `/api/suscripciones/confirmar?token=${token}`), 200, 'confirmar')
})

// ---------------------------------------------------------------------------------------------
console.log('\nReporte ciudadano, consenso y tiempo real')
const sse = await escucharSse().catch((e) => {
  fallaron.push('SSE')
  console.log(`  ✘ GET /api/sectores/stream\n      ${e.message}`)
  return { eventos: [], cerrar() {} }
})
let primerReporte
let subidaDelPrimerReporte

await paso('POST /api/reportes con un dispositivo por reporte hasta alcanzar el consenso', async () => {
  const puntos = await puntosPorBarrio(API)
  const ids = []
  for (let i = 0; i < 6; i++) {
    const dispositivo = await nuevoDispositivo()
    const r = esperarEstado(
      await http('POST', '/api/reportes', {
        cabeceras: { 'X-Dispositivo': dispositivo },
        cuerpo: { tipo: 'SIN_AGUA', sectorId: sector.id, coordenada: puntos.get(sector.id), precisionMetros: 10 },
      }),
      201,
      `reporte ${i + 1}`,
    )
    ids.push(r.json.id)
    if (i === 0) subidaDelPrimerReporte = r.json.subidaToken
    exigir(r.json.sectorId === sector.id, 'el sectorId de la respuesta no es el declarado')
    exigir(r.json.verificacion === 'UBICACION_VERIFICADA', `el reporte salió ${r.json.verificacion} y se esperaba UBICACION_VERIFICADA`)
  }
  primerReporte = ids[0]
  await new Promise((ok) => setTimeout(ok, 1500))
  const s = (await http('GET', `/api/sectores/${sector.id}`)).json
  exigir(s.estado === 'SIN_SERVICIO', `tras 6 reportes el sector sigue en ${s.estado} (esperado SIN_SERVICIO)`)
  return `${sector.id} → ${s.estado}`
})

await paso('el reporte por coordenada infiere el sector', async () => {
  const r = esperarEstado(
    await http('POST', '/api/reportes', {
      cabeceras: { 'X-Dispositivo': await nuevoDispositivo() },
      cuerpo: {
        tipo: 'PRESION_BAJA',
        coordenada: { latitud: 10.4012, longitud: -75.556 },
      },
    }),
    201,
    'reporte por coordenada',
  )
  exigir(r.json.sectorId, 'no devolvió el sector inferido')
  return `→ ${r.json.sectorId}`
})

await paso('adjuntar una foto y confirmar un reporte ajeno', async () => {
  const formulario = new FormData()
  formulario.append('foto', new Blob([PNG_1X1], { type: 'image/png' }), 'prueba.png')
  const foto = esperarEstado(
    await http('POST', `/api/reportes/${primerReporte}/foto`, { formulario, cabeceras: { 'X-Subida': subidaDelPrimerReporte } }),
    200,
    'foto',
  )
  exigir(foto.json.fotoUrl, 'la respuesta no trae fotoUrl')
  exigir(foto.json.fotoEstado === 'EN_REVISION', `fotoEstado ${foto.json.fotoEstado} (esperado EN_REVISION)`)
  // Una foto no es pública hasta que el reporte se aprueba (ADR-091): hasta entonces responde 404, el mismo que si no existiera.
  registrarCobertura('GET', foto.json.fotoUrl.startsWith('/') ? foto.json.fotoUrl : `/${foto.json.fotoUrl}`)
  const visible = await fetch(`${API}${foto.json.fotoUrl.startsWith('/') ? '' : '/'}${foto.json.fotoUrl}`)
  exigir(visible.status === 404, `la foto de un reporte sin aprobar debería dar 404 y dio ${visible.status}`)
  const c = esperarEstado(
    await http('POST', `/api/reportes/${primerReporte}/confirmar`, { cabeceras: { 'X-Dispositivo': await nuevoDispositivo() } }),
    200,
    'confirmar',
  )
  exigir(c.json.confirmaciones >= 1, 'no subió el contador de confirmaciones')
})

await paso('el stream SSE avisó del cambio de estado', async () => {
  const visto = sse.eventos.join('')
  exigir(visto.includes('event:sectores') || visto.includes('event: sectores'), `sin evento «sectores»; llegó: ${visto.slice(0, 120)}`)
})
sse.cerrar()

await paso('el suscriptor confirmado recibió el aviso y puede darse de baja', async () => {
  let aviso
  for (let i = 0; i < 20 && !aviso; i++) {
    const correos = await correosPara(correoSuscriptor, { intentos: 1 })
    // El de confirmación trae «confirmar?token»; el aviso de cambio solo trae el enlace de baja (a la SPA: /avisos/baja?token=).
    aviso = correos.find((c) => c.cuerpo.includes('baja?token=') && !c.cuerpo.includes('confirmar?token='))
    if (!aviso) await new Promise((ok) => setTimeout(ok, 500))
  }
  exigir(aviso, 'no llegó el correo de cambio de estado')
  tokenBaja = tokenDeEnlace(aviso.cuerpo, 'baja')
  // «¿Ya volvió el agua?»: el mismo aviso trae el enlace para decirlo (a la SPA: /sectores/{id}/restablecimiento?token=).
  const tokenRestablecimiento = tokenDeEnlace(aviso.cuerpo, 'restablecimiento')
  esperarEstado(
    await http('POST', `/api/sectores/${sector.id}/restablecimiento?token=${tokenRestablecimiento}`),
    201,
    'restablecimiento del sector',
  )
  // El GET de cancelar solo muestra la página con el botón; la baja es el POST.
  esperarEstado(await http('GET', `/api/suscripciones/cancelar?token=${tokenBaja}`, { cabeceras: { Accept: 'text/html' } }), 200, 'página de baja')
  esperarEstado(await http('POST', `/api/suscripciones/cancelar?token=${tokenBaja}`), 200, 'cancelar')
})

// ---------------------------------------------------------------------------------------------
console.log('\nHistoria pública')
await paso('bitácora paginada por cabeceras, sustento y un evento del consenso', async () => {
  const r = esperarEstado(await http('GET', '/api/bitacora?pagina=0&tamano=20'), 200, 'bitácora')
  exigir(r.cabeceras.get('x-total-count') !== null, 'sin X-Total-Count')
  exigir(Array.isArray(r.json) && r.json.length > 0, 'la bitácora está vacía tras un consenso')
  const evento = r.json[0]
  const s = await http('GET', `/api/bitacora/${evento.id}/sustento`)
  exigir(s.estado === 200 && Array.isArray(s.json), `sustento ${s.estado}`)
  return `${r.cabeceras.get('x-total-count')} eventos`
})

await paso('estadísticas, exportación CSV, cumplimiento y histórico de cortes', async () => {
  esperarEstado(await http('GET', '/api/estadisticas'), 200, 'estadísticas')
  registrarCobertura('GET', '/api/estadisticas/exportar.csv')
  const csv = await fetch(`${API}/api/estadisticas/exportar.csv`)
  exigir(csv.status === 200 && (csv.headers.get('content-type') ?? '').includes('csv'), `CSV ${csv.status}`)
  // Sin ningún corte cerrado no hay nada que medir: 400 documentado, no un fallo (se repite al cerrar uno).
  const global = await http('GET', '/api/cumplimiento')
  exigir([200, 400].includes(global.estado), `cumplimiento global: ${global.estado}`)
  esperarEstado(await http('GET', `/api/sectores/${sector.id}/cortes`), 200, 'histórico de cortes')
  esperarEstado(await http('GET', '/api/v2/requests.json'), 200, 'Open311')
})

// ---------------------------------------------------------------------------------------------
console.log('\nPanel: sesión, segundo factor y permisos')
let token
let cuentaObservador
let totpSecreto = process.env.TOTP_SECRETO
  ?? (process.env.TOTP_ARCHIVO && existsSync(process.env.TOTP_ARCHIVO) ? readFileSync(process.env.TOTP_ARCHIVO, 'utf8').trim() : undefined)

await paso('las rutas del panel exigen sesión (401 sin token)', async () => {
  esperarEstado(await http('GET', '/api/veedor/yo'), 401, 'yo sin token')
  esperarEstado(await http('GET', '/api/veedor/usuarios'), 401, 'usuarios sin token')
})

if (!ADMIN_CLAVE) {
  fallaron.push('sesión del ADMIN')
  console.log('  ✘ falta ADMIN_CLAVE: sin ella no se puede iniciar sesión y se omite el resto del panel')
} else {
  await paso('login del ADMIN y alta del segundo factor (TOTP)', async () => {
    // Un código TOTP solo vale una vez: si otro script lo gastó en esta franja de 30 s, el ayudante espera a la siguiente.
    const sesion = await iniciarSesionAdmin({
      api: API,
      correo: ADMIN_CORREO,
      clave: ADMIN_CLAVE,
      totpSecreto,
      registrar: registrarCobertura,
    })
    token = sesion.token
    totpSecreto = sesion.totpSecreto
    if (!sesion.secretoNuevo) return 'con el segundo factor ya dado de alta'
    if (process.env.TOTP_ARCHIVO) {
      writeFileSync(process.env.TOTP_ARCHIVO, `${totpSecreto}\n`)
      return 'segundo factor dado de alta; secreto guardado en TOTP_ARCHIVO'
    }
    return `secreto TOTP nuevo (${totpSecreto}); guárdalo si quieres repetir el script`
  })

  if (token) {
    await paso('GET /api/veedor/yo devuelve la cuenta y sus permisos', async () => {
      const r = esperarEstado(await http('GET', '/api/veedor/yo', { token }), 200, 'yo')
      const permisos = r.json.permisosEfectivos ?? r.json.permisos
      exigir(permisos?.length > 0, 'sin permisosEfectivos[]')
      return `${r.json.rol}, ${permisos.length} permisos`
    })

    let corteId
    await paso('registrar y cerrar un corte oficial (GESTIONAR_CORTES)', async () => {
      const ahora = Date.now()
      const creado = esperarEstado(
        await http('POST', '/api/veedor/cortes', {
          token,
          cuerpo: {
            sectoresAfectados: [sector.id],
            inicio: new Date(ahora).toISOString(),
            finPrometido: new Date(ahora + 4 * 3600e3).toISOString(),
            causa: `Prueba de flujos ${SUFIJO}`,
          },
        }),
        201,
        'crear corte',
      )
      corteId = creado.json.id
      esperarEstado(
        await http('PATCH', `/api/veedor/cortes/${corteId}/cierre`, {
          token,
          cuerpo: { horaReal: new Date(ahora + 3600e3).toISOString() },
        }),
        200,
        'cerrar corte',
      )
      esperarEstado(await http('GET', `/api/veedor/cortes?sectorId=${sector.id}`, { token }), 200, 'listar cortes')
      esperarEstado(await http('GET', '/api/cumplimiento'), 200, 'cumplimiento global con un corte cerrado')
      esperarEstado(await http('GET', `/api/cumplimiento/sectores/${sector.id}`), 200, 'cumplimiento del sector')
      esperarEstado(await http('GET', `/api/cumplimiento/cortes/${corteId}`), 200, 'cumplimiento del corte')
    })

    await paso('cola de moderación, propuestas de ingesta y salud de los colectores', async () => {
      const pendientes = esperarEstado(await http('GET', '/api/veedor/reportes/pendientes', { token }), 200, 'pendientes')
      const reporte = Array.isArray(pendientes.json) ? pendientes.json[0] : pendientes.json?.contenido?.[0]
      if (reporte) {
        esperarEstado(await http('PATCH', `/api/veedor/reportes/${reporte.id}/aprobar`, { token }), 200, 'aprobar')
      }
      esperarEstado(await http('GET', '/api/veedor/ingesta/propuestas', { token }), 200, 'propuestas')
      esperarEstado(await http('GET', '/api/veedor/ingesta/salud', { token }), 200, 'salud')
      esperarEstado(await http('GET', '/api/veedor/ingesta/fallidos', { token }), 200, 'fallidos')
    })

    await paso('ADMIN: invitar una cuenta, aceptar la invitación y auditar', async () => {
      const correo = `veedor-${SUFIJO}@example.com`
      const invitado = esperarEstado(
        await http('POST', '/api/veedor/usuarios/invitaciones', {
          token,
          cuerpo: { correo, nombre: `Veedor ${SUFIJO}`, rol: 'OBSERVADOR' },
        }),
        201,
        'invitar',
      )
      const correos = await correosPara(correo)
      exigir(correos.length > 0, 'no llegó el correo de invitación')
      const tokenInvitacion = tokenDeEnlace(correos[0].cuerpo, 'invitacion')
      esperarEstado(
        await http('POST', '/api/cuentas/invitacion', {
          cuerpo: { token: tokenInvitacion, clave: `Clave-de-prueba-${SUFIJO}-2026` },
        }),
        204,
        'aceptar invitación',
      )
      const login = esperarEstado(
        await http('POST', '/api/veedor/sesion', {
          cuerpo: { correo, clave: `Clave-de-prueba-${SUFIJO}-2026` },
        }),
        200,
        'login del invitado',
      )
      exigir(login.json.alcance === 'COMPLETO', `alcance ${login.json.alcance}`)
      const prohibido = await http('POST', '/api/veedor/cortes', {
        token: login.json.token,
        cuerpo: { sectoresAfectados: [sector.id], inicio: new Date().toISOString(), finPrometido: new Date(Date.now() + 3600e3).toISOString(), causa: 'x' },
      })
      exigir(prohibido.estado === 403, `un OBSERVADOR no debería gestionar cortes y recibió ${prohibido.estado}`)
      esperarEstado(await http('GET', '/api/veedor/usuarios?pagina=0&tamano=10', { token }), 200, 'listar cuentas')
      const auditoria = esperarEstado(await http('GET', '/api/veedor/auditoria', { token }), 200, 'auditoría')
      exigir(auditoria.json !== null, 'auditoría vacía de formato')
      return `${invitado.json.id ?? 'invitación creada'}; el OBSERVADOR recibe 403 al gestionar cortes`
    })

    await paso('cortes: ver uno, cerrar un barrio, confirmar un cierre, ver vencidos y anular (GESTIONAR_CORTES)', async () => {
      const otros = sectores.filter((s) => s.id !== sector.id)
      const [a, b, c] = [otros[10].id, otros[11].id, otros[12].id]
      const ahora = Date.now()
      const crearCorte = async (barrios, causa) =>
        esperarEstado(
          await http('POST', '/api/veedor/cortes', {
            token,
            cuerpo: {
              sectoresAfectados: barrios,
              inicio: new Date(ahora).toISOString(),
              finPrometido: new Date(ahora + 4 * 3600e3).toISOString(),
              causa: `${causa} ${SUFIJO}`,
            },
          }),
          201,
          `crear corte «${causa}»`,
        ).json.id
      const dosBarrios = await crearCorte([a, b], 'Corte de dos barrios')
      esperarEstado(await http('GET', `/api/veedor/cortes/${dosBarrios}`, { token }), 200, 'ver el corte')
      esperarEstado(
        await http('PATCH', `/api/veedor/cortes/${dosBarrios}/sectores/${a}/cierre`, {
          token,
          cuerpo: { horaReal: new Date(ahora + 3600e3).toISOString() },
        }),
        200,
        'cerrar un barrio del corte',
      )
      // Confirmar el cierre provisional de un barrio exige que los vecinos lo hayan cerrado antes y que el corte esté vencido; aquí
      // solo se comprueba que la ruta responde con el conflicto documentado (409). El camino feliz lo recorre el guion de simulación.
      const confirmacion = await http('PATCH', `/api/veedor/cortes/${dosBarrios}/sectores/${b}/confirmacion`, {
        token,
        cuerpo: { horaReal: new Date(ahora + 3600e3).toISOString() },
      })
      exigir([200, 409].includes(confirmacion.estado), `confirmar el cierre de un barrio: ${confirmacion.estado} ${confirmacion.texto.slice(0, 160)}`)
      esperarEstado(await http('GET', '/api/veedor/cortes/vencidos', { token }), 200, 'cortes vencidos')
      const aAnular = await crearCorte([c], 'Corte que se anula')
      esperarEstado(
        await http('PATCH', `/api/veedor/cortes/${aAnular}/anulacion`, { token, cuerpo: { motivo: `Prueba de flujos ${SUFIJO}` } }),
        200,
        'anular el corte',
      )
      return `confirmación de un barrio → ${confirmacion.estado}`
    })

    await paso('moderación: ver la foto del panel, descartar la foto y descartar el reporte', async () => {
      const barrio = sectores.filter((s) => s.id !== sector.id)[20].id
      const puntos = await puntosPorBarrio(API)
      const reporte = esperarEstado(
        await http('POST', '/api/reportes', {
          cabeceras: { 'X-Dispositivo': await nuevoDispositivo() },
          cuerpo: { tipo: 'SIN_AGUA', sectorId: barrio, coordenada: puntos.get(barrio), precisionMetros: 10 },
        }),
        201,
        'reporte para moderar',
      )
      const formulario = new FormData()
      formulario.append('foto', new Blob([PNG_1X1], { type: 'image/png' }), 'moderacion.png')
      const foto = esperarEstado(
        await http('POST', `/api/reportes/${reporte.json.id}/foto`, { formulario, cabeceras: { 'X-Subida': reporte.json.subidaToken } }),
        200,
        'foto del reporte',
      )
      const nombre = foto.json.fotoUrl.split('/').pop()
      registrarCobertura('GET', `/api/veedor/fotos/${nombre}`)
      const vista = await fetch(`${API}/api/veedor/fotos/${nombre}`, { headers: { Authorization: `Bearer ${token}` } })
      exigir(vista.status === 200 && (vista.headers.get('content-type') ?? '').startsWith('image/'), `foto del panel: ${vista.status}`)
      esperarEstado(await http('PATCH', `/api/veedor/reportes/${reporte.json.id}/foto/descartar`, { token }), 200, 'descartar la foto')
      esperarEstado(await http('PATCH', `/api/veedor/reportes/${reporte.json.id}/descartar`, { token }), 200, 'descartar el reporte')
      esperarEstado(await http('GET', '/api/veedor/disputas', { token }), 200, 'disputas')
    })

    await paso('ingesta: descartar, aprobar y anular propuestas', async () => {
      const lista = esperarEstado(await http('GET', '/api/veedor/ingesta/propuestas?pagina=0&tamano=100', { token }), 200, 'propuestas')
      const pendientes = lista.json.filter((p) => p.estadoRevision === 'PENDIENTE')
      exigir(pendientes.length >= 2, `hacen falta 2 propuestas pendientes y hay ${pendientes.length} (estados: ${[...new Set(lista.json.map((p) => p.estadoRevision))].join(', ')})`)
      esperarEstado(await http('PATCH', `/api/veedor/ingesta/propuestas/${pendientes[0].id}/descartar`, { token }), 200, 'descartar')
      // Aprobar publica el corte en el mapa; se anula enseguida para no dejarlo puesto.
      esperarEstado(await http('PATCH', `/api/veedor/ingesta/propuestas/${pendientes[1].id}/aprobar`, { token }), 200, 'aprobar')
      esperarEstado(
        await http('PATCH', `/api/veedor/ingesta/propuestas/${pendientes[1].id}/anulacion`, { token, cuerpo: { motivo: `Prueba de flujos ${SUFIJO}` } }),
        200,
        'anular lo aprobado',
      )
      esperarEstado(await http('GET', '/api/veedor/sistema/metricas', { token }), 200, 'métricas del sistema')
      return `${pendientes.length} propuestas pendientes`
    })

    await paso('cuentas del panel: registro, reenvío, verificación (JSON y página), aprobación, suspensión, reactivación, permisos, rechazo', async () => {
      const crear = async (etiqueta) => {
        const correo = `${etiqueta}-${SUFIJO}@example.com`
        const clave = `Clave-${etiqueta}-${SUFIJO}-2026`
        esperarEstado(
          await http('POST', '/api/cuentas/registro', { cuerpo: { correo, nombre: `Cuenta ${etiqueta} ${SUFIJO}`, clave, barrioId: sector.id } }),
          202,
          `registro de ${etiqueta}`,
        )
        return { correo, clave }
      }
      const idPendiente = async (correo) => {
        const lista = esperarEstado(
          await http('GET', '/api/veedor/usuarios?estado=PENDIENTE_APROBACION&pagina=0&tamano=200', { token }),
          200,
          'cuentas pendientes',
        )
        const cuenta = lista.json.find((u) => u.correo === correo)
        exigir(cuenta, `${correo} no aparece entre las cuentas pendientes de aprobación`)
        return cuenta.id
      }

      // La primera: pide otro correo (el reenvío invalida el primer enlace) y verifica con la página HTML.
      const aprobada = await crear('aprobada')
      const primerEnlace = await esperarToken(aprobada.correo, 'verificar')
      esperarEstado(await http('POST', '/api/cuentas/verificacion/reenvio', { cuerpo: { correo: aprobada.correo } }), 202, 'reenvío de la verificación')
      const enlace = await esperarToken(aprobada.correo, 'verificar', { excluir: [primerEnlace] })
      const pagina = await http('GET', `/api/cuentas/enlaces/verificar?token=${enlace}`, { cabeceras: { Accept: 'text/html' } })
      exigir(pagina.estado === 200 && pagina.texto.includes('<'), `página de verificación: ${pagina.estado}`)
      esperarEstado(
        await http('POST', '/api/cuentas/enlaces/verificar', { formulario: new URLSearchParams({ token: enlace }), cabeceras: { Accept: 'text/html' } }),
        200,
        'verificar desde la página',
      )
      const idAprobada = await idPendiente(aprobada.correo)
      esperarEstado(await http('PATCH', `/api/veedor/usuarios/${idAprobada}/aprobacion`, { token, cuerpo: { rol: 'OBSERVADOR', concedidos: [], revocados: [] } }), 200, 'aprobar')
      esperarEstado(await http('PATCH', `/api/veedor/usuarios/${idAprobada}/suspension`, { token }), 200, 'suspender')
      esperarEstado(await http('PATCH', `/api/veedor/usuarios/${idAprobada}/reactivacion`, { token }), 200, 'reactivar')
      esperarEstado(
        await http('PATCH', `/api/veedor/usuarios/${idAprobada}/permisos`, { token, cuerpo: { rol: 'OBSERVADOR', concedidos: ['MODERAR_REPORTES'], revocados: [] } }),
        200,
        'conceder un permiso',
      )

      // La segunda: se verifica con el POST JSON y el ADMIN la rechaza.
      const rechazada = await crear('rechazada')
      const enlaceRechazada = await esperarToken(rechazada.correo, 'verificar')
      esperarEstado(await http('POST', `/api/cuentas/verificacion?token=${enlaceRechazada}`), 204, 'verificar con JSON')
      esperarEstado(await http('PATCH', `/api/veedor/usuarios/${await idPendiente(rechazada.correo)}/rechazo`, { token }), 200, 'rechazar')

      cuentaObservador = { ...aprobada, id: idAprobada }
      return 'una cuenta aprobada con un permiso extra y otra rechazada'
    })

    await paso('el observador: segundo factor (alta, confirmación y baja) y cambio de su clave', async () => {
      exigir(cuentaObservador, 'falta la cuenta del paso anterior')
      const login = esperarEstado(
        await http('POST', '/api/veedor/sesion', { cuerpo: { correo: cuentaObservador.correo, clave: cuentaObservador.clave } }),
        200,
        'login del observador',
      )
      exigir(login.json.alcance === 'COMPLETO', `alcance ${login.json.alcance}`)
      let sesionObservador = login.json.token
      const alta = esperarEstado(await http('POST', '/api/veedor/segundo-factor/alta', { token: sesionObservador }), 200, 'alta del segundo factor')
      const secreto = alta.json.secreto
      const confirmado = esperarEstado(
        await http('POST', '/api/veedor/segundo-factor/confirmacion', { token: sesionObservador, cuerpo: { codigo: codigoTotp(secreto) } }),
        200,
        'confirmar el segundo factor',
      )
      sesionObservador = confirmado.json?.token ?? sesionObservador
      // El código de la confirmación ya se gastó en esta franja de 30 s: la baja necesita el de la siguiente.
      await esperarSiguienteFranjaTotp()
      esperarEstado(await http('POST', '/api/veedor/segundo-factor/baja', { token: sesionObservador, cuerpo: { codigo: codigoTotp(secreto) } }), 204, 'baja del segundo factor')
      // Dar de baja el segundo factor cierra las sesiones de la cuenta: hay que volver a entrar para cambiar la clave.
      esperarEstado(await http('GET', '/api/veedor/yo', { token: sesionObservador }), 401, 'la sesión anterior a la baja')
      const nueva = esperarEstado(
        await http('POST', '/api/veedor/sesion', { cuerpo: { correo: cuentaObservador.correo, clave: cuentaObservador.clave } }),
        200,
        'entrar otra vez sin segundo factor',
      )
      exigir(nueva.json.alcance === 'COMPLETO', `alcance ${nueva.json.alcance}`)
      esperarEstado(
        await http('POST', '/api/veedor/cuenta/clave', { token: nueva.json.token, cuerpo: { claveActual: cuentaObservador.clave, claveNueva: `${cuentaObservador.clave}-nueva` } }),
        204,
        'cambiar la propia clave',
      )
    })

    await paso('invitación: reenviar el correo y aceptarla desde la página', async () => {
      const correo = `pagina-${SUFIJO}@example.com`
      const invitada = esperarEstado(
        await http('POST', '/api/veedor/usuarios/invitaciones', { token, cuerpo: { correo, nombre: `Invitada ${SUFIJO}`, rol: 'OBSERVADOR' } }),
        201,
        'invitar',
      )
      const primer = await esperarToken(correo, 'invitacion')
      esperarEstado(await http('POST', `/api/veedor/usuarios/${invitada.json.id}/invitacion/reenvio`, { token }), 202, 'reenviar la invitación')
      const enlace = await esperarToken(correo, 'invitacion', { excluir: [primer] })
      const pagina = await http('GET', `/api/cuentas/enlaces/invitacion?token=${enlace}`, { cabeceras: { Accept: 'text/html' } })
      exigir(pagina.estado === 200 && pagina.texto.includes('<'), `página de la invitación: ${pagina.estado}`)
      esperarEstado(
        await http('POST', '/api/cuentas/enlaces/invitacion', {
          formulario: new URLSearchParams({ token: enlace, clave: `Clave-pagina-${SUFIJO}-2026` }),
          cabeceras: { Accept: 'text/html' },
        }),
        200,
        'aceptar desde la página',
      )
    })

    await paso('cerrar sesión revoca el token', async () => {
      // El filtro JWT acepta a propósito un token emitido en el mismo segundo de la revocación (iat tiene precisión de
      // segundo). Sin esta espera, el paso falla a veces si todo el panel cabe en el segundo del login.
      await new Promise((resolver) => setTimeout(resolver, 1100))
      esperarEstado(await http('POST', '/api/veedor/sesion/cierre', { token }), 204, 'cierre')
      esperarEstado(await http('GET', '/api/veedor/yo', { token }), 401, 'token revocado')
    })
  }
}

// ---------------------------------------------------------------------------------------------
console.log('\nVecino registrado')
const correoVecino = `vecino-registrado-${SUFIJO}@example.com`
const claveVecino = `Clave-vecino-${SUFIJO}-2026`
let sesionVecino

await paso('alta del vecino sin clave y activación desde la página del correo', async () => {
  esperarEstado(
    await http('POST', '/api/cuentas/vecino', {
      cuerpo: { correo: correoVecino, nombre: `Vecino ${SUFIJO}`, barrioId: sector.id, consentimiento: { privacidad: true, avisos: true } },
    }),
    202,
    'alta del vecino',
  )
  const enlace = await esperarToken(correoVecino, 'invitacion')
  const pagina = await http('GET', `/api/cuentas/enlaces/invitacion?token=${enlace}`, { cabeceras: { Accept: 'text/html' } })
  exigir(pagina.estado === 200 && pagina.texto.includes('<'), `página de activación: ${pagina.estado}`)
  esperarEstado(
    await http('POST', '/api/cuentas/enlaces/invitacion', {
      formulario: new URLSearchParams({ token: enlace, clave: claveVecino }),
      cabeceras: { Accept: 'text/html' },
    }),
    200,
    'elegir la clave',
  )
})

await paso('sesión del vecino: perfil, cambio de datos, verificación del barrio y cierre', async () => {
  const login = esperarEstado(await http('POST', '/api/vecino/sesion', { cuerpo: { correo: correoVecino, clave: claveVecino } }), 200, 'login del vecino')
  sesionVecino = login.json.token
  esperarEstado(await http('GET', '/api/vecino/yo', { token: sesionVecino }), 200, 'perfil')
  esperarEstado(await http('PATCH', '/api/vecino/perfil', { token: sesionVecino, cuerpo: { recibirAvisos: false } }), 200, 'cambiar el perfil')
  const puntos = await puntosPorBarrio(API)
  const verificado = await http('POST', '/api/vecino/verificacion-barrio', {
    token: sesionVecino,
    cuerpo: { coordenada: puntos.get(sector.id), precisionMetros: 10 },
  })
  exigir([200, 422].includes(verificado.estado), `verificación del barrio: ${verificado.estado} ${verificado.texto.slice(0, 160)}`)
  // El filtro JWT acepta a propósito un token emitido en el mismo segundo de la revocación (iat tiene precisión de segundo).
  await new Promise((ok) => setTimeout(ok, 1100))
  esperarEstado(await http('POST', '/api/vecino/sesion/cierre', { token: sesionVecino }), 204, 'cierre de sesión')
  esperarEstado(await http('GET', '/api/vecino/yo', { token: sesionVecino }), 401, 'perfil con la sesión cerrada')
  return `verificación del barrio → ${verificado.estado}`
})

await paso('restablecer la clave: por JSON y desde la página del correo', async () => {
  esperarEstado(await http('POST', '/api/cuentas/restablecimiento', { cuerpo: { correo: correoVecino } }), 202, 'pedir el restablecimiento')
  const primero = await esperarToken(correoVecino, 'restablecer')
  esperarEstado(await http('POST', '/api/cuentas/clave', { cuerpo: { token: primero, clave: `${claveVecino}-uno` } }), 204, 'cambiar la clave con JSON')
  esperarEstado(await http('POST', '/api/cuentas/restablecimiento', { cuerpo: { correo: correoVecino } }), 202, 'pedirlo otra vez')
  const segundo = await esperarToken(correoVecino, 'restablecer', { excluir: [primero] })
  const pagina = await http('GET', `/api/cuentas/enlaces/restablecer?token=${segundo}`, { cabeceras: { Accept: 'text/html' } })
  exigir(pagina.estado === 200 && pagina.texto.includes('<'), `página de restablecimiento: ${pagina.estado}`)
  esperarEstado(
    await http('POST', '/api/cuentas/enlaces/restablecer', {
      formulario: new URLSearchParams({ token: segundo, clave: `${claveVecino}-dos` }),
      cabeceras: { Accept: 'text/html' },
    }),
    200,
    'cambiar la clave desde la página',
  )
  esperarEstado(
    await http('POST', '/api/vecino/sesion', { cuerpo: { correo: correoVecino, clave: `${claveVecino}-dos` } }),
    200,
    'entrar con la clave nueva',
  )
})

// ---------------------------------------------------------------------------------------------
console.log('\nSistema y sensores')
await paso('modo del sistema (REAL) y las rutas de simulación no existen en la instancia real', async () => {
  const modo = esperarEstado(await http('GET', '/api/sistema/modo'), 200, 'modo')
  exigir(JSON.stringify(modo.json).includes('REAL'), `el modo no es REAL: ${modo.texto.slice(0, 120)}`)
  const sim = await http('POST', '/api/sim/reloj', { cuerpo: {} })
  exigir(sim.estado === 404, `/api/sim/** debería dar 404 en la instancia real y dio ${sim.estado}`)
})

await paso('Índice de Cumplimiento: serie, serie en CSV y calidad del dato', async () => {
  esperarEstado(await http('GET', '/api/cumplimiento/serie'), 200, 'serie')
  const csv = esperarEstado(await http('GET', '/api/cumplimiento/serie.csv', { cabeceras: { Accept: 'text/csv' } }), 200, 'serie en CSV')
  exigir((csv.cabeceras.get('content-type') ?? '').includes('csv'), 'la serie no es CSV')
  esperarEstado(await http('GET', '/api/cumplimiento/calidad'), 200, 'calidad')
})

await paso('sensor de presión: sin clave y con una clave falsa no entra', async () => {
  const cuerpo = { sensorId: `sensor-${SUFIJO}`, sectorId: sector.id, presionPsi: 30 }
  const sinClave = await http('POST', '/api/iot/presion', { cuerpo })
  const claveFalsa = await http('POST', '/api/iot/presion', { cuerpo, cabeceras: { 'X-IoT-Key': 'x'.repeat(40) } })
  // 503 mientras IOT_KEY esté vacía (lo normal: el soporte de sensores está construido pero inactivo); 401 si hay clave y esta no es.
  exigir([401, 503].includes(sinClave.estado), `sin clave: ${sinClave.estado}`)
  exigir([401, 503].includes(claveFalsa.estado), `con clave falsa: ${claveFalsa.estado}`)
  if (process.env.IOT_KEY) {
    esperarEstado(await http('POST', '/api/iot/presion', { cuerpo, cabeceras: { 'X-IoT-Key': process.env.IOT_KEY } }), 200, 'con la clave buena')
  }
  return `sin clave → ${sinClave.estado}, clave falsa → ${claveFalsa.estado}`
})

// ---------------------------------------------------------------------------------------------
const sinRecorrer =PLANTILLAS.filter((p) => !recorridas.has(p.clave) && !p.clave.includes(' /api/sim/')).map((p) => p.clave)
console.log(`\nCobertura del contrato: ${recorridas.size} de ${recorridas.size + sinRecorrer.length} operaciones recorridas (sin contar /api/sim/**)`)
if (sinRecorrer.length) {
  console.log('Sin recorrer:')
  for (const clave of sinRecorrer) console.log(`  - ${clave}`)
  if (process.env.EXIGIR_COBERTURA === '1') fallaron.push(`${sinRecorrer.length} operaciones sin recorrer`)
}

console.log(`\n${pasaron} pasos correctos, ${fallaron.length} con fallo${fallaron.length ? `: ${fallaron.join(' · ')}` : ''}\n`)
process.exit(fallaron.length ? 1 : 0)
