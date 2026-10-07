// Cliente HTTP mínimo de la API de AguaVigía para el simulador. Habla como lo haría cualquier cliente real: JSON, `Authorization: Bearer` para
// las sesiones, `X-Dispositivo` para la identidad anónima y `X-Sim-Key` solo en las rutas /api/sim/**. Los errores salen como ErrorDeApi con el
// estado y el problema RFC 7807 que devolvió el servidor.

export class ErrorDeApi extends Error {
  constructor(metodo, ruta, estado, cuerpo) {
    const problema = cuerpo !== null && typeof cuerpo === 'object' ? cuerpo : {};
    super(`${metodo} ${ruta} respondió ${estado}${problema.detail ? `: ${problema.detail}` : problema.title ? `: ${problema.title}` : ''}`);
    this.name = 'ErrorDeApi';
    this.estado = estado;
    this.tipo = problema.type ?? '';
    this.problema = problema;
  }
}

async function leerCuerpo(respuesta) {
  const texto = await respuesta.text();
  if (texto === '') return null;
  try {
    return JSON.parse(texto);
  } catch {
    return texto;
  }
}

export class ClienteApi {
  constructor({ base, clave }) {
    this.base = base.replace(/\/+$/, '');
    this.clave = clave;
  }

  async #enviar(metodo, ruta, { cuerpo, formulario, token, dispositivo, cabeceras = {}, esperados }) {
    const encabezados = { Accept: 'application/json', ...cabeceras };
    if (token) encabezados.Authorization = `Bearer ${token}`;
    if (dispositivo) encabezados['X-Dispositivo'] = dispositivo;
    let datos;
    if (formulario) {
      datos = formulario;
    } else if (cuerpo !== undefined) {
      encabezados['Content-Type'] = 'application/json';
      datos = JSON.stringify(cuerpo);
    }
    const respuesta = await fetch(`${this.base}${ruta}`, { method: metodo, headers: encabezados, body: datos });
    const contenido = await leerCuerpo(respuesta);
    const aceptado = esperados ? esperados.includes(respuesta.status) : respuesta.status >= 200 && respuesta.status < 300;
    if (!aceptado) throw new ErrorDeApi(metodo, ruta, respuesta.status, contenido);
    return { estado: respuesta.status, cuerpo: contenido, cabeceras: respuesta.headers };
  }

  get(ruta, opciones = {}) {
    return this.#enviar('GET', ruta, opciones);
  }

  post(ruta, cuerpo, opciones = {}) {
    return this.#enviar('POST', ruta, { ...opciones, cuerpo });
  }

  patch(ruta, cuerpo, opciones = {}) {
    return this.#enviar('PATCH', ruta, { ...opciones, cuerpo });
  }

  /** Una ruta de simulación: lleva la clave de simulación. */
  sim(metodo, ruta, cuerpo, opciones = {}) {
    return this.#enviar(metodo, ruta, { ...opciones, cuerpo, cabeceras: { ...opciones.cabeceras, 'X-Sim-Key': this.clave } });
  }

  /** multipart/form-data (la foto de un reporte); fetch pone el límite del formulario. */
  enviarFormulario(ruta, formulario, opciones = {}) {
    return this.#enviar('POST', ruta, { ...opciones, formulario });
  }
}
