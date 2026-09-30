// Fábrica de cuentas de demostración COMPLETAS y coherentes: usuario con barrio real, estado, rol y permisos, y lo que esa
// cuenta dejaría en el sistema (tokens_cuenta, auditoria_cuentas, suscripciones). La comparten:
//   - sembrar-usuarios-demo.mjs: las 30 000 iniciales, deterministas (misma semilla, mismos datos).
//   - agregar-usuarios.mjs: ampliaciones en vivo con faker, distintas en cada ejecución (ADR-087).
// Quien la usa decide de dónde sale el azar y los nombres; el resto de las reglas vive solo aquí.

import { createHash, createHmac, randomBytes } from 'node:crypto';
import bcrypt from 'bcryptjs';

// La clave de las cuentas de demostración NO está en el repositorio: sale de CLAVE_DEMO si la defines al sembrar, o se
// genera al azar en cada ejecución (y el script que siembra la imprime una sola vez). Una clave fija y pública compartida
// por miles de cuentas VEEDOR sería una puerta abierta para cualquiera que lea el repo y llegue a la API.
export const CLAVE_DEMO = process.env.CLAVE_DEMO || `Demo-${randomBytes(9).toString('base64url')}`;
export const HASH_CLAVE_DEMO = bcrypt.hashSync(CLAVE_DEMO, 10);
export const COLECCIONES_SEMBRADAS = ['usuarios', 'tokens_cuenta', 'auditoria_cuentas', 'suscripciones'];

const PAQUETE = 'com.aguavigia.ctg.infrastructure.persistence.mongo.';
const CLASE = `${PAQUETE}UsuarioDocumento`;
const CLASE_TOKEN = `${PAQUETE}TokenCuentaDocumento`;
const CLASE_AUDITORIA = `${PAQUETE}EventoAuditoriaDocumento`;
const CLASE_SUSCRIPCION = `${PAQUETE}SuscripcionDocumento`;

const DOMINIOS = [['gmail.com', 55], ['hotmail.com', 14], ['outlook.com', 10], ['yahoo.es', 6], ['yahoo.com', 5], ['hotmail.es', 3],
  ['live.com', 3], ['icloud.com', 2], ['proton.me', 1], ['outlook.es', 1]];

export const sinAcentos = (texto) => texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/ñ/g, 'n').replace(/[^a-z0-9]/g, '');

const DIA = 24 * 60 * 60 * 1000;
const HORA = 60 * 60 * 1000;
const MINUTO = 60 * 1000;
const ALFABETO_BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const IP_ADMIN = '172.18.0.1';

const hashDeToken = (token) => createHash('sha256').update(token, 'utf8').digest('hex');
// Los tokens de invitación y verificación de las cuentas sembradas se derivan de un secreto que solo existe durante esta
// ejecución: nadie puede calcularlos desde el repositorio ni desde el id de la cuenta, y el claro no se guarda en ninguna parte.
const SECRETO_DE_TOKENS = randomBytes(32);
const tokenDeDemo = (usuarioId) => createHmac('sha256', SECRETO_DE_TOKENS).update(usuarioId).digest('base64url');

/**
 * @param azar      () => número en [0, 1): el generador de aleatoriedad (con semilla o no).
 * @param nombrar   (herramientas) => { nombre, apellido1, apellido2 }: de dónde salen los nombres.
 * @param ahora     instante de referencia en milisegundos; ninguna fecha generada lo supera.
 * @param extras    campos que se añaden a cada documento generado (p. ej. { lote }).
 */
export function crearFabricaDeCuentas({ azar, nombrar, ahora = Date.now(), extras = {} }) {
  const entero = (min, max) => min + Math.floor(azar() * (max - min + 1));
  const elegir = (lista) => lista[Math.floor(azar() * lista.length)];
  function elegirPonderado(pares) {
    const total = pares.reduce((suma, [, peso]) => suma + peso, 0);
    let r = azar() * total;
    for (const [valor, peso] of pares) {
      r -= peso;
      if (r < 0) return valor;
    }
    return pares[pares.length - 1][0];
  }
  const herramientas = { azar, entero, elegir, elegirPonderado };

  function uuid() {
    const bytes = Array.from({ length: 16 }, () => entero(0, 255));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const h = bytes.map((b) => b.toString(16).padStart(2, '0')).join('');
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
  }

  function estiloDeCorreo(nombre, apellido1, apellido2) {
    const n = sinAcentos(nombre.split(' ')[0]);
    const nCompleto = sinAcentos(nombre);
    const a1 = sinAcentos(apellido1);
    const a2 = sinAcentos(apellido2);
    const anio = entero(1968, 2006);
    const dos = String(entero(10, 99));
    return elegirPonderado([
      [`${n}.${a1}`, 26], [`${n}${a1}`, 18], [`${n}.${a1}${anio}`, 12], [`${a1}.${n}`, 8], [`${n[0]}${a1}${dos}`, 9],
      [`${n}_${a1}`, 6], [`${nCompleto}${a1[0]}${a2[0]}`, 5], [`${n}.${a1}.${a2}`, 5], [`${a1}${a2[0]}${n[0]}${anio % 100}`, 4],
      [`${n}${anio}`, 4], [`${nCompleto}${dos}`, 3],
    ]);
  }

  const desde = ahora - 540 * DIA;
  function fechaDeCreacion() {
    // Sesgo hacia lo reciente: la raíz cuadrada de un uniforme reparte más cuentas en los últimos meses.
    return new Date(desde + Math.sqrt(azar()) * (ahora - desde - DIA));
  }

  // 160 bits en Base32, el mismo tamaño y alfabeto que genera el backend (RFC 4648, sin relleno).
  const secretoTotp = () => Array.from({ length: 32 }, () => ALFABETO_BASE32[entero(0, 31)]).join('');
  const ipCiudadana = () => `190.${entero(24, 255)}.${entero(0, 255)}.${entero(1, 254)}`;

  const correosUsados = new Set();
  const nombresUsados = new Set();
  let admin = null;

  // Barrios reales: el peso de cada uno es su población, con un piso para que los sectores pequeños también aparezcan.
  let barrios = [];
  let pesosAcumulados = [];
  function cargarBarrios(sectores) {
    barrios = sectores.map((s) => s.slug);
    let suma = 0;
    pesosAcumulados = sectores.map((s) => (suma += Math.max(Number(s.poblacion) || 0, 300)));
  }
  function elegirBarrio() {
    const r = azar() * pesosAcumulados[pesosAcumulados.length - 1];
    let bajo = 0;
    let alto = pesosAcumulados.length - 1;
    while (bajo < alto) {
      const medio = (bajo + alto) >> 1;
      if (pesosAcumulados[medio] > r) alto = medio;
      else bajo = medio + 1;
    }
    return barrios[bajo];
  }

  function crearCuenta() {
    // Cada nombre completo es único: dos cuentas nunca comparten persona, solo eso ya las distingue a simple vista.
    let nombre, apellido1, apellido2;
    do {
      ({ nombre, apellido1, apellido2 } = nombrar(herramientas));
    } while (nombresUsados.has(`${nombre} ${apellido1} ${apellido2}`));
    nombresUsados.add(`${nombre} ${apellido1} ${apellido2}`);
    const dominio = elegirPonderado(DOMINIOS);
    const base = estiloDeCorreo(nombre, apellido1, apellido2);
    let correo = `${base}@${dominio}`;
    for (let intento = 2; correosUsados.has(correo); intento++) correo = `${base}${intento}@${dominio}`;
    correosUsados.add(correo);

    const id = uuid();
    const estado = elegirPonderado([['ACTIVA', 62], ['PENDIENTE_APROBACION', 12], ['PENDIENTE_VERIFICACION', 9],
      ['INVITADA', 7], ['SUSPENDIDA', 6], ['RECHAZADA', 4]]);
    // Quien se registra solo nace como OBSERVADOR; el rol distinto lo decide quien invita o aprueba.
    const rol = ['PENDIENTE_VERIFICACION', 'PENDIENTE_APROBACION', 'RECHAZADA'].includes(estado)
      ? 'OBSERVADOR'
      : elegirPonderado([['OBSERVADOR', 55], ['VEEDOR', 45]]);

    // Permisos sueltos sobre el rol: nunca concedidos y revocados a la vez, ni se toca el del segundo factor.
    let concedidos = [];
    let revocados = [];
    if (estado === 'ACTIVA' || estado === 'SUSPENDIDA') {
      if (rol === 'OBSERVADOR' && azar() < 0.05) concedidos = [elegir(['MODERAR_REPORTES', 'REVISAR_INGESTA'])];
      if (rol === 'VEEDOR' && azar() < 0.04) revocados = [elegir(['MODERAR_REPORTES', 'REVISAR_INGESTA', 'GESTIONAR_CORTES'])];
    }

    // Un enlace vigente solo existe si la cuenta es reciente: el de verificación dura 48 h y el de invitación 7 días.
    const creadoEn = estado === 'PENDIENTE_VERIFICACION'
      ? new Date(ahora - MINUTO - azar() * 46 * HORA)
      : estado === 'INVITADA'
        ? new Date(ahora - MINUTO - azar() * 6.5 * DIA)
        : fechaDeCreacion();

    const barrio = elegirBarrio();
    const conSegundoFactor = rol === 'VEEDOR' && (estado === 'ACTIVA' || estado === 'SUSPENDIDA') && azar() < 0.4;
    const secreto = conSegundoFactor ? secretoTotp() : null;

    // Rastro de auditoría coherente con el estado: cada evento ocurre después del anterior y nunca en el futuro.
    const auditoria = [];
    let instante = creadoEn.getTime();
    let ultimo = instante;
    const yo = { id, correo };
    const registrar = (accion, autor, detalle, ip, minutosMaximos = 2880) => {
      auditoria.push({
        _id: uuid(),
        accion,
        autorId: autor?.id ?? null,
        autorCorreo: autor?.correo ?? null,
        sujetoId: id,
        sujetoCorreo: correo,
        detalle,
        ip,
        ocurrioEn: new Date(instante),
        datosDeDemostracion: true,
        _class: CLASE_AUDITORIA,
        ...extras,
      });
      ultimo = instante;
      instante = Math.min(ahora, instante + entero(5, minutosMaximos) * MINUTO);
    };
    const porInvitacion = estado === 'INVITADA'
      || (rol === 'VEEDOR' && (estado === 'ACTIVA' || estado === 'SUSPENDIDA') && azar() < 0.5);
    if (porInvitacion) {
      registrar('CUENTA_INVITADA', admin, `Invitada con el rol ${rol}`, IP_ADMIN);
      if (estado !== 'INVITADA') registrar('INVITACION_ACEPTADA', yo, 'Aceptó la invitación y fijó su clave', ipCiudadana());
    } else {
      registrar('CUENTA_REGISTRADA', null, 'Auto-registro; queda pendiente de verificar correo', ipCiudadana());
      if (estado !== 'PENDIENTE_VERIFICACION') registrar('CORREO_VERIFICADO', yo, 'Verificó su correo con el enlace recibido', ipCiudadana());
      if (estado === 'RECHAZADA') registrar('CUENTA_RECHAZADA', admin, 'Solicitud de acceso rechazada', IP_ADMIN, 20000);
      if (estado === 'ACTIVA' || estado === 'SUSPENDIDA') registrar('CUENTA_APROBADA', admin, 'Solicitud de acceso aprobada', IP_ADMIN, 20000);
    }
    if (conSegundoFactor) registrar('SEGUNDO_FACTOR_ACTIVADO', yo, 'Dio de alta la app de autenticación', ipCiudadana());
    if (estado === 'SUSPENDIDA') registrar('CUENTA_SUSPENDIDA', admin, 'Suspendida por un administrador', IP_ADMIN, 43200);

    const tokens = [];
    if (estado === 'PENDIENTE_VERIFICACION' || estado === 'INVITADA') {
      const vigenciaHoras = estado === 'INVITADA' ? 7 * 24 : 48;
      tokens.push({
        _id: hashDeToken(tokenDeDemo(id)),
        tipo: estado === 'INVITADA' ? 'INVITACION' : 'VERIFICACION_CORREO',
        usuarioId: id,
        creadoEn,
        usadoEn: null,
        expiraEn: new Date(creadoEn.getTime() + vigenciaHoras * HORA),
        datosDeDemostracion: true,
        _class: CLASE_TOKEN,
        ...extras,
      });
    }

    // Alertas por correo: solo quien ya tiene la cuenta activa suscribe su barrio (y, a veces, uno vecino).
    const suscripciones = [];
    if (estado === 'ACTIVA') {
      const sorteo = azar();
      const estadoSuscripcion = sorteo < 0.35 ? 'CONFIRMADA' : sorteo < 0.39 ? 'PENDIENTE_CONFIRMACION' : sorteo < 0.42 ? 'CANCELADA' : null;
      if (estadoSuscripcion) {
        const sectorIds = [barrio];
        if (azar() < 0.25) {
          const otro = elegirBarrio();
          if (otro !== barrio) sectorIds.push(otro);
        }
        const idSuscripcion = uuid();
        suscripciones.push({
          _id: idSuscripcion,
          // Al cancelar, el backend anonimiza el correo: se siembra igual para que la colección se vea como la real.
          correo: estadoSuscripcion === 'CANCELADA' ? `baja-${idSuscripcion}@correo-eliminado.invalid` : correo,
          sectorIds,
          estado: estadoSuscripcion,
          tokenConfirmacion: uuid(),
          creadaEn: new Date(Math.min(ahora, creadoEn.getTime() + entero(60, 14 * 24 * 60) * MINUTO)),
          datosDeDemostracion: true,
          _class: CLASE_SUSCRIPCION,
          ...extras,
        });
      }
    }

    const usuario = {
      _id: id,
      correo,
      nombre: `${nombre} ${apellido1} ${apellido2}`,
      claveHash: estado === 'INVITADA' ? null : HASH_CLAVE_DEMO,
      estado,
      rol,
      barrio,
      permisosConcedidos: concedidos,
      permisosRevocados: revocados,
      secretoTotp: secreto,
      segundoFactorConfirmadoEn: secreto ? new Date(ultimoDelSegundoFactor(auditoria)) : null,
      creadoEn,
      actualizadoEn: new Date(ultimo),
      datosDeDemostracion: true,
      _class: CLASE,
      ...extras,
    };
    return { usuario, tokens, auditoria, suscripciones };
  }

  return {
    herramientas,
    cargarBarrios,
    crearCuenta,
    correosUsados,
    fijarAdmin: (cuenta) => { admin = cuenta; },
  };
}

function ultimoDelSegundoFactor(auditoria) {
  return auditoria.find((e) => e.accion === 'SEGUNDO_FACTOR_ACTIVADO').ocurrioEn.getTime();
}
