// Inicio de sesión del ADMIN con segundo factor, compartido por los scripts que recorren el panel (ADR-039).
// Igual que scripts/verificar-flujos.mjs: con TOTP_SECRETO entra directo; sin él, si la cuenta aún no dio de alta su segundo
// factor (base recién creada), lo da de alta y devuelve el secreto nuevo para que quien llama lo guarde.
import { codigoTotp } from '../codigo-totp.mjs'

async function peticion(api, metodo, ruta, { token, cuerpo, registrar } = {}) {
  registrar?.(metodo, ruta)
  const init = { method: metodo, headers: { Accept: 'application/json' } }
  if (token) init.headers.Authorization = `Bearer ${token}`
  if (cuerpo !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(cuerpo)
  }
  const respuesta = await fetch(`${api}${ruta}`, init)
  const texto = await respuesta.text()
  return { estado: respuesta.status, json: texto ? JSON.parse(texto) : null }
}

const exigir = (condicion, mensaje) => {
  if (!condicion) throw new Error(mensaje)
}

/** @returns {Promise<{token:string, totpSecreto:string, secretoNuevo:boolean}>} */
export async function iniciarSesionAdmin({ api, correo, clave, totpSecreto, registrar }) {
  exigir(clave, 'falta ADMIN_CLAVE (la clave cuyo hash está en VEEDOR_PASSWORD_HASH)')
  if (totpSecreto) {
    const ingresar = () => peticion(api, 'POST', '/api/veedor/sesion', {
      cuerpo: { correo, clave, codigoTotp: codigoTotp(totpSecreto) },
      registrar,
    })
    let r = await ingresar()
    if (r.estado === 401) {
      // Un código TOTP solo vale una vez: si otro script lo gastó en esta misma franja de 30 s, se espera a la siguiente.
      await new Promise((ok) => setTimeout(ok, 30_500 - (Date.now() % 30_000)))
      r = await ingresar()
    }
    exigir(r.estado === 200, `el ingreso con segundo factor respondió ${r.estado}`)
    return { token: r.json.token, totpSecreto, secretoNuevo: false }
  }
  const login = await peticion(api, 'POST', '/api/veedor/sesion', { cuerpo: { correo, clave }, registrar })
  exigir(login.estado === 200, `el ingreso respondió ${login.estado}`)
  exigir(
    login.json.alcance === 'ALTA_SEGUNDO_FACTOR',
    `alcance ${login.json.alcance}: la cuenta ya tiene segundo factor; pasa TOTP_SECRETO`,
  )
  const alta = await peticion(api, 'POST', '/api/veedor/segundo-factor/alta', { token: login.json.token, registrar })
  exigir(alta.estado === 200, `el alta del segundo factor respondió ${alta.estado}`)
  const secreto = alta.json.secreto
  const confirmado = await peticion(api, 'POST', '/api/veedor/segundo-factor/confirmacion', {
    token: login.json.token,
    cuerpo: { codigo: codigoTotp(secreto) },
    registrar,
  })
  exigir(confirmado.estado === 200 && confirmado.json.alcance === 'COMPLETO', `la confirmación respondió ${confirmado.estado}`)
  return { token: confirmado.json.token, totpSecreto: secreto, secretoNuevo: true }
}
