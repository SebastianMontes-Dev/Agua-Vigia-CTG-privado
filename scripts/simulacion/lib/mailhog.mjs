// Lee los correos que el backend manda a Mailhog (lo que vería una persona) para sacar de ellos los tokens de los enlaces: así los vecinos y las
// cuentas del panel de la simulación se activan por el camino real, sin atajos en la base. Mailhog v1: GET /api/v2/messages, DELETE /api/v1/messages.

export function decodificarQuotedPrintable(texto) {
  return texto
    .replace(/=\r?\n/g, '')
    .replace(/=([0-9A-Fa-f]{2})/g, (_, hex) => String.fromCharCode(Number.parseInt(hex, 16)));
}

/**
 * El token del primer enlace `/api/cuentas/enlaces/{ruta}?token=…` del cuerpo, ya decodificado, o null si no hay. Una `ruta` que empieza por «/»
 * es la ruta completa (p. ej. «/avisos/confirmar», el enlace de las suscripciones, que lleva a las pantallas del frontend).
 */
export function extraerToken(cuerpo, ruta) {
  const texto = decodificarQuotedPrintable(cuerpo ?? '');
  const fragmento = ruta.startsWith('/') ? ruta : `/api/cuentas/enlaces/${ruta}`;
  const patron = new RegExp(`${fragmento.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\?token=([^"'\\s<>&]+)`);
  const coincidencia = patron.exec(texto);
  return coincidencia ? decodeURIComponent(coincidencia[1]) : null;
}

const direccion = (para) => `${para.Mailbox}@${para.Domain}`.toLowerCase();

/** correo → token de la ruta pedida. Mailhog entrega primero el más reciente: de cada destinatario manda el primero que aparezca. */
export function tokensPorDestinatario(mensajes, ruta) {
  const tokens = new Map();
  for (const mensaje of mensajes) {
    const token = extraerToken(mensaje.Content?.Body, ruta);
    if (token === null) continue;
    for (const para of mensaje.To ?? []) {
      const correo = direccion(para);
      if (!tokens.has(correo)) tokens.set(correo, token);
    }
  }
  return tokens;
}

/** Todos los mensajes de Mailhog, de página en página (el más reciente primero). */
export async function leerCorreos(base, { pagina = 250, maximo = 20_000 } = {}) {
  const mensajes = [];
  for (let inicio = 0; inicio < maximo; inicio += pagina) {
    const respuesta = await fetch(`${base}/api/v2/messages?start=${inicio}&limit=${pagina}`);
    if (!respuesta.ok) throw new Error(`Mailhog respondió ${respuesta.status} en ${base}/api/v2/messages`);
    const cuerpo = await respuesta.json();
    mensajes.push(...cuerpo.items);
    if (mensajes.length >= cuerpo.total || cuerpo.items.length === 0) break;
  }
  return mensajes;
}

const pausa = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

/** Espera hasta que haya un token para cada correo (los correos salen en segundo plano) y los devuelve; falla con los que faltan. */
export async function esperarTokens(base, correos, ruta, { espera = 60_000, intervalo = 500 } = {}) {
  const limite = Date.now() + espera;
  const pedidos = correos.map((c) => c.toLowerCase());
  for (;;) {
    const tokens = tokensPorDestinatario(await leerCorreos(base), ruta);
    const faltan = pedidos.filter((c) => !tokens.has(c));
    if (faltan.length === 0) return tokens;
    if (Date.now() > limite) {
      throw new Error(`Mailhog no recibió el correo con enlace '${ruta}' de ${faltan.length} cuenta(s) (p. ej. ${faltan.slice(0, 3).join(', ')}) en ${espera / 1000} s`);
    }
    await pausa(intervalo);
  }
}

/** Cuántos correos hay para una dirección con un asunto que contenga `fragmentoDeAsunto`. */
export function contarCorreos(mensajes, correo, fragmentoDeAsunto) {
  const buscado = correo.toLowerCase();
  return mensajes.filter((m) => (m.To ?? []).some((p) => direccion(p) === buscado)
    && (m.Content?.Headers?.Subject ?? []).some((a) => a.includes(fragmentoDeAsunto))).length;
}

/** Vacía Mailhog (el reinicio de la simulación). */
export async function vaciarBuzon(base) {
  const respuesta = await fetch(`${base}/api/v1/messages`, { method: 'DELETE' });
  if (!respuesta.ok) throw new Error(`Mailhog respondió ${respuesta.status} al vaciar el buzón`);
}

/** Los ids de los correos dirigidos a una dirección del `dominio` dado (los de la simulación llevan siempre el mismo). */
export function idsDeCorreosPara(mensajes, dominio) {
  const buscado = dominio.toLowerCase();
  return mensajes.filter((m) => (m.To ?? []).some((p) => String(p.Domain).toLowerCase() === buscado)).map((m) => m.ID);
}

/**
 * Borra solo los correos de la simulación. El Mailhog del perfil `simulacion` es el mismo que usa la instancia real, así que el reinicio no puede
 * vaciar el buzón entero (`vaciarBuzon`): se llevaría los correos de quien esté probando la instancia real.
 */
export async function vaciarCorreosDeSimulacion(base, dominio) {
  const ids = idsDeCorreosPara(await leerCorreos(base), dominio);
  for (const id of ids) {
    const respuesta = await fetch(`${base}/api/v1/messages/${encodeURIComponent(id)}`, { method: 'DELETE' });
    if (!respuesta.ok) throw new Error(`Mailhog respondió ${respuesta.status} al borrar el correo ${id}`);
  }
  return ids.length;
}
