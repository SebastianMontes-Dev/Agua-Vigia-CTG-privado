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
 */
import { randomBytes } from 'node:crypto'
import { codigoTotp } from './codigo-totp.mjs'

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
  const coincidencia = new RegExp(`${fragmentoDeRuta}\\?token=([A-Za-z0-9_\\-]+)`).exec(cuerpo)
  exigir(coincidencia, `no hay un enlace «${fragmentoDeRuta}» en el correo`)
  return coincidencia[1]
}

async function escucharSse() {
  const control = new AbortController()
  const eventos = []
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

await paso('POST /api/reportes con huellas distintas hasta alcanzar el consenso', async () => {
  const ids = []
  for (let i = 0; i < 6; i++) {
    const r = esperarEstado(
      await http('POST', '/api/reportes', {
        cuerpo: { tipo: 'SIN_AGUA', huella: randomBytes(16).toString('hex') + `${SUFIJO}${i}`.padEnd(6, '0'), sectorId: sector.id },
      }),
      201,
      `reporte ${i + 1}`,
    )
    ids.push(r.json.id)
    exigir(r.json.sectorId === sector.id, 'el sectorId de la respuesta no es el declarado')
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
      cuerpo: {
        tipo: 'PRESION_BAJA',
        huella: randomBytes(20).toString('hex'),
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
  const foto = esperarEstado(await http('POST', `/api/reportes/${primerReporte}/foto`, { formulario }), 200, 'foto')
  exigir(foto.json.fotoUrl, 'la respuesta no trae fotoUrl')
  const visible = await fetch(`${API}${foto.json.fotoUrl.startsWith('/') ? '' : '/'}${foto.json.fotoUrl}`)
  exigir(visible.status === 200, `la foto no se sirve (${visible.status})`)
  const c = esperarEstado(
    await http('POST', `/api/reportes/${primerReporte}/confirmar`, { cuerpo: { huella: randomBytes(20).toString('hex') } }),
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
    // El de confirmación trae «confirmar?token»; el aviso de cambio solo trae el enlace de baja.
    aviso = correos.find((c) => c.cuerpo.includes("cancelar?token=") && !c.cuerpo.includes("confirmar?token="))
    if (!aviso) await new Promise((ok) => setTimeout(ok, 500))
  }
  exigir(aviso, 'no llegó el correo de cambio de estado')
  tokenBaja = tokenDeEnlace(aviso.cuerpo, 'cancelar')
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
let totpSecreto = process.env.TOTP_SECRETO

await paso('las rutas del panel exigen sesión (401 sin token)', async () => {
  esperarEstado(await http('GET', '/api/veedor/yo'), 401, 'yo sin token')
  esperarEstado(await http('GET', '/api/veedor/usuarios'), 401, 'usuarios sin token')
})

if (!ADMIN_CLAVE) {
  fallaron.push('sesión del ADMIN')
  console.log('  ✘ falta ADMIN_CLAVE: sin ella no se puede iniciar sesión y se omite el resto del panel')
} else {
  await paso('login del ADMIN y alta del segundo factor (TOTP)', async () => {
    if (totpSecreto) {
      const completa = esperarEstado(
        await http('POST', '/api/veedor/sesion', {
          cuerpo: { correo: ADMIN_CORREO, clave: ADMIN_CLAVE, codigoTotp: codigoTotp(totpSecreto) },
        }),
        200,
        'login con TOTP',
      )
      token = completa.json.token
      return 'con el segundo factor ya dado de alta'
    }
    const login = esperarEstado(
      await http('POST', '/api/veedor/sesion', { cuerpo: { correo: ADMIN_CORREO, clave: ADMIN_CLAVE } }),
      200,
      'login',
    )
    if (login.json.alcance === 'ALTA_SEGUNDO_FACTOR') {
      const alta = esperarEstado(
        await http('POST', '/api/veedor/segundo-factor/alta', { token: login.json.token }),
        200,
        'alta TOTP',
      )
      totpSecreto = alta.json.secreto
      const confirmado = esperarEstado(
        await http('POST', '/api/veedor/segundo-factor/confirmacion', {
          token: login.json.token,
          cuerpo: { codigo: codigoTotp(totpSecreto) },
        }),
        200,
        'confirmar TOTP',
      )
      exigir(confirmado.json.alcance === 'COMPLETO', `alcance ${confirmado.json.alcance}`)
      token = confirmado.json.token
      return `secreto TOTP nuevo (${totpSecreto}); guárdalo si quieres repetir el script`
    }
    exigir(false, `el login devolvió alcance ${login.json.alcance}; se esperaba ALTA_SEGUNDO_FACTOR`)
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

    await paso('cerrar sesión revoca el token', async () => {
      esperarEstado(await http('POST', '/api/veedor/sesion/cierre', { token }), 204, 'cierre')
      esperarEstado(await http('GET', '/api/veedor/yo', { token }), 401, 'token revocado')
    })
  }
}

console.log(`\n${pasaron} pasos correctos, ${fallaron.length} con fallo${fallaron.length ? `: ${fallaron.join(' · ')}` : ''}\n`)
process.exit(fallaron.length ? 1 : 0)
