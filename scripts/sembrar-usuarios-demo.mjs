#!/usr/bin/env node
// Siembra cuentas de demostración COMPLETAS para presentar el proyecto con una base grande (por defecto
// 30 000) y variada. Cada cuenta lleva nombre y apellidos distintos, correo, barrio real (repartido según la
// población de cada sector), uno de los seis estados, el rol OBSERVADOR o VEEDOR, permisos sueltos y fechas
// repartidas en los últimos 18 meses; parte de los VEEDOR tiene además el segundo factor (TOTP) dado de alta.
// Y, para que la base sea coherente y no solo `usuarios`, siembra lo que esas cuentas dejarían en el sistema:
//   - tokens_cuenta:      enlace de verificación (PENDIENTE_VERIFICACION) o de invitación (INVITADA) vigente.
//   - auditoria_cuentas:  el rastro del alta, la aprobación, el rechazo, la suspensión y el segundo factor.
//   - suscripciones:      alertas por correo de las cuentas activas, ligadas a su barrio.
//
// Uso:
//   cd scripts && npm install
//   node sembrar-usuarios-demo.mjs                       # 30 000 cuentas
//   node sembrar-usuarios-demo.mjs --cantidad 5000 --semilla 7 --minimo 0
//
// Al terminar imprime los conteos por colección, estado, rol y barrio, y sale con error si `usuarios` queda
// por debajo de --minimo (30 000 por defecto): la entrega exige al menos esa cifra.
//
// Requiere los sectores sembrados (`sembrar-sectores.mjs`): el barrio de cada cuenta es uno de ellos.
//
// Variables: MONGODB_URI (por defecto mongodb://localhost:27017/?directConnection=true) y
// MONGODB_DB (por defecto aguavigia).
//
// IMPORTANTE — orden:
//   1. Arranca el backend con ADMIN_INICIAL_CORREO y VEEDOR_PASSWORD_HASH para que cree al ADMIN. Ese ADMIN solo
//      se crea si NO existe ninguna cuenta: si siembras estas antes, nunca se crea.
//   2. Después corre este script.
//
// Es determinista (misma semilla, mismos datos) e idempotente: antes de insertar borra únicamente lo que él mismo
// sembró (marca `datosDeDemostracion: true`, también en tokens, auditoría y suscripciones); jamás toca cuentas
// reales ni al ADMIN. Si la aplicación vuelve a guardar una cuenta de demostración (por ejemplo, un ADMIN la
// suspende), pierde esa marca y deja de contarse como sembrada.
//
// Las cuentas con segundo factor (TOTP) guardan su secreto en la base: para iniciar sesión con ellas hace falta
// el código, que se calcula con `node codigo-totp.mjs <secreto>`. Las pruebas de carga usan las que no lo tienen.
//
// Las cuentas ACTIVAS comparten una clave de demostración, DemoAguaVigia-2026 (solo para entrar a probar como un
// veedor u observador; no hay ADMIN entre ellas). Por eso el script se niega a correr contra una base que no sea
// local, salvo que pases --permitir-remoto. Los correos usan dominios reales de proveedores como los genera
// cualquier dato de prueba: no los uses con un SMTP real (el compose de desarrollo envía a Mailhog).

import { MongoClient } from 'mongodb';
import { createHash } from 'node:crypto';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
  options: {
    cantidad: { type: 'string', default: '30000' },
    semilla: { type: 'string', default: '2026' },
    minimo: { type: 'string', default: '30000' },
    'permitir-remoto': { type: 'boolean', default: false },
  },
});
const CANTIDAD = Number(values.cantidad);
const SEMILLA = Number(values.semilla);
const MINIMO = Number(values.minimo);
// directConnection=true: Mongo local es un replica set de un nodo; sin esto el driver
// descubre que el miembro se anuncia como `mongo:27017` (nombre solo resoluble dentro de
// Docker) e intenta reconectarse ahi.
const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';

// BCrypt (coste 10) de «DemoAguaVigia-2026», calculado con la misma biblioteca que usa el backend.
const HASH_CLAVE_DEMO = '$2a$10$9Z7IzVuDwklPgBYmu8noLeYWAUyXtjYWkfdBCYi282zRzSANE/256';
const PAQUETE = 'com.aguavigia.ctg.infrastructure.persistence.mongo.';
const CLASE = `${PAQUETE}UsuarioDocumento`;
const CLASE_TOKEN = `${PAQUETE}TokenCuentaDocumento`;
const CLASE_AUDITORIA = `${PAQUETE}EventoAuditoriaDocumento`;
const CLASE_SUSCRIPCION = `${PAQUETE}SuscripcionDocumento`;

if (!Number.isInteger(CANTIDAD) || CANTIDAD < 1 || CANTIDAD > 500000) {
  console.error('--cantidad debe ser un entero entre 1 y 500000');
  process.exit(1);
}
if (!Number.isInteger(MINIMO) || MINIMO < 0) {
  console.error('--minimo debe ser un entero mayor o igual que 0');
  process.exit(1);
}
if (!/^mongodb:\/\/(localhost|127\.0\.0\.1|\[::1\])([:/]|$)/.test(MONGODB_URI) && !values['permitir-remoto']) {
  console.error(`Me niego a sembrar cuentas de demostración en ${MONGODB_URI.replace(/\/\/.*@/, '//***@')}: no es local.\n`
    + 'Si de verdad es lo que quieres, repite con --permitir-remoto.');
  process.exit(1);
}

// ---- generador pseudoaleatorio con semilla (mulberry32): misma semilla, mismos datos ----
function generador(semilla) {
  let a = semilla >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const azar = generador(SEMILLA);
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

const NOMBRES = ['Sebastián', 'Valentina', 'Santiago', 'Mariana', 'Juan David', 'Camila', 'Andrés', 'Laura', 'Carlos', 'Daniela',
  'Luis', 'Sofía', 'Miguel Ángel', 'Isabella', 'Jorge', 'María Fernanda', 'Felipe', 'Paula', 'Alejandro', 'Natalia',
  'Daniel', 'Luisa', 'Nicolás', 'Andrea', 'Kevin', 'Yulieth', 'Wilmer', 'Leidy', 'Yesid', 'Keiner', 'Yaneth', 'Jhon',
  'Ana', 'Pedro', 'Karen', 'Óscar', 'Diana', 'Rafael', 'Ángela', 'Héctor', 'Lucía', 'Edwin', 'Marcela', 'Iván', 'Tatiana',
  'Cristian', 'Johana', 'Fabián', 'Milena', 'Ricardo', 'Vanessa', 'Julián', 'Carolina', 'Esteban', 'Liliana', 'Mauricio',
  'Claudia', 'Hernán', 'Patricia', 'Gustavo', 'Sandra', 'Álvaro', 'Yolanda', 'Fernando', 'Adriana', 'Rodrigo', 'Beatriz',
  'Camilo', 'Lorena', 'Sergio', 'Gloria', 'Mateo', 'Salomé', 'Emiliano', 'Antonella', 'Samuel', 'Gabriela', 'Joaquín',
  'Manuela', 'Tomás', 'Juliana', 'Emmanuel', 'Shirley', 'Brayan', 'Stefany', 'Jefferson', 'Dayana', 'Anderson', 'Maryuri',
  'Duván', 'Yurleidis', 'Dairo', 'Ledys', 'Jairo', 'Nelly', 'Eduardo', 'Rosario', 'Ramiro', 'Cecilia', 'Arturo', 'Inés',
  'Enrique', 'Martha', 'Guillermo', 'Piedad', 'Orlando', 'Flor', 'Alberto', 'Rocío', 'Ángel', 'Nubia', 'Pablo', 'Jimena',
  'Ignacio', 'Aura', 'Leonardo', 'Yesenia', 'Alexis', 'Marlene', 'Cristóbal', 'Genoveva', 'Aníbal', 'Dilia', 'Wilfrido', 'Erika'];
const APELLIDOS = ['García', 'Rodríguez', 'Martínez', 'López', 'González', 'Pérez', 'Sánchez', 'Ramírez', 'Torres', 'Díaz', 'Vargas',
  'Castro', 'Moreno', 'Jiménez', 'Ruiz', 'Herrera', 'Medina', 'Aguilar', 'Rojas', 'Ortiz', 'Gutiérrez', 'Chávez', 'Mendoza',
  'Barrios', 'Julio', 'Ospino', 'Mercado', 'Támara', 'Villalobos', 'Caballero', 'Buelvas', 'Arrieta', 'Hoyos', 'Tapia',
  'Pombo', 'Marrugo', 'Padilla', 'De la Hoz', 'Guerrero', 'Genes', 'Berrío', 'Cassiani', 'Pájaro', 'Cantillo', 'Cárdenas',
  'Navarro', 'Ríos', 'Vega', 'Cabrera', 'Fernández', 'Suárez', 'Romero', 'Salcedo', 'Blanco', 'Ibarra', 'Peña', 'Acosta',
  'Cortés', 'Delgado', 'Domínguez', 'Escobar', 'Franco', 'Guzmán', 'Hernández', 'Lara', 'Mejía', 'Nieto', 'Ochoa', 'Pineda',
  'Quintero', 'Rivera', 'Salas', 'Trujillo', 'Valdés', 'Zapata', 'Arango', 'Bermúdez', 'Cuesta', 'Duque', 'Espinosa', 'Fuentes',
  'Giraldo', 'Henao', 'Lozano', 'Montoya', 'Naranjo', 'Osorio', 'Palacios', 'Restrepo', 'Serrano', 'Tovar', 'Urrutia',
  'Villarreal', 'Yepes', 'Zabaleta', 'Alvarado', 'Bolaños', 'Camargo', 'Diazgranados', 'Echeverría', 'Fontalvo', 'Gamarra',
  'Hurtado', 'Iglesias', 'Jaramillo', 'Lambis', 'Maldonado', 'Núñez', 'Orozco', 'Polo', 'Quiroz', 'Redondo', 'Sarmiento',
  'Tatis', 'Uribe', 'Vergara', 'Watts', 'Ariza', 'Bello', 'Coronado', 'Doria', 'Escorcia', 'Florez', 'Gómez', 'Herazo'];
const DOMINIOS = [['gmail.com', 55], ['hotmail.com', 14], ['outlook.com', 10], ['yahoo.es', 6], ['yahoo.com', 5], ['hotmail.es', 3],
  ['live.com', 3], ['icloud.com', 2], ['proton.me', 1], ['outlook.es', 1]];

const sinAcentos = (texto) => texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/ñ/g, 'n').replace(/[^a-z0-9]/g, '');

const AHORA = Date.now();
const DIA = 24 * 60 * 60 * 1000;

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

const FECHAS = { desde: AHORA - 540 * DIA };
function fechaDeCreacion() {
  // Sesgo hacia lo reciente: la raíz cuadrada de un uniforme reparte más cuentas en los últimos meses.
  return new Date(FECHAS.desde + Math.sqrt(azar()) * (AHORA - FECHAS.desde - DIA));
}

const HORA = 60 * 60 * 1000;
const MINUTO = 60 * 1000;
const ALFABETO_BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const IP_ADMIN = '172.18.0.1';

// 160 bits en Base32, el mismo tamaño y alfabeto que genera el backend (RFC 4648, sin relleno).
const secretoTotp = () => Array.from({ length: 32 }, () => ALFABETO_BASE32[entero(0, 31)]).join('');
const hashDeToken = (token) => createHash('sha256').update(token, 'utf8').digest('hex');
// El token en claro de una cuenta sembrada es `demo-token-<id>`: solo sirve contra esta base local de demostración.
const tokenDeDemo = (usuarioId) => `demo-token-${usuarioId}`;
const ipCiudadana = () => `190.${entero(24, 255)}.${entero(0, 255)}.${entero(1, 254)}`;

const usados = new Set();
const nombresUsados = new Set();

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

let admin = null;

function crearCuenta() {
  // Cada nombre completo es único: dos cuentas nunca comparten persona, solo eso ya las distingue a simple vista.
  let nombre, apellido1, apellido2;
  do {
    nombre = azar() < 0.22 ? `${elegir(NOMBRES)} ${elegir(NOMBRES)}` : elegir(NOMBRES);
    apellido1 = elegir(APELLIDOS);
    apellido2 = elegir(APELLIDOS);
    if (apellido2 === apellido1) apellido2 = elegir(APELLIDOS);
  } while (nombresUsados.has(`${nombre} ${apellido1} ${apellido2}`));
  nombresUsados.add(`${nombre} ${apellido1} ${apellido2}`);
  const dominio = elegirPonderado(DOMINIOS);
  const base = estiloDeCorreo(nombre, apellido1, apellido2);
  let correo = `${base}@${dominio}`;
  for (let intento = 2; usados.has(correo); intento++) correo = `${base}${intento}@${dominio}`;
  usados.add(correo);

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
    ? new Date(AHORA - MINUTO - azar() * 46 * HORA)
    : estado === 'INVITADA'
      ? new Date(AHORA - MINUTO - azar() * 6.5 * DIA)
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
    });
    ultimo = instante;
    instante = Math.min(AHORA, instante + entero(5, minutosMaximos) * MINUTO);
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
        creadaEn: new Date(Math.min(AHORA, creadoEn.getTime() + entero(60, 14 * 24 * 60) * MINUTO)),
        datosDeDemostracion: true,
        _class: CLASE_SUSCRIPCION,
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
  };
  return { usuario, tokens, auditoria, suscripciones };
}

function ultimoDelSegundoFactor(auditoria) {
  return auditoria.find((e) => e.accion === 'SEGUNDO_FACTOR_ACTIVADO').ocurrioEn.getTime();
}

const COLECCIONES_SEMBRADAS = ['usuarios', 'tokens_cuenta', 'auditoria_cuentas', 'suscripciones'];

// Cuando la aplicación usa un token o modifica una cuenta sembrada, esa fila pierde la marca `datosDeDemostracion`
// y la limpieza previa ya no la ve; al sembrar de nuevo su _id (determinista) choca. Se omite y se cuenta en vez de
// abortar: la fila que la app tocó es la que manda.
const omitidos = {};
async function escribir(nombre, coleccion, documentos) {
  if (documentos.length === 0) return;
  try {
    await coleccion.insertMany(documentos, { ordered: false });
  } catch (error) {
    const errores = error.writeErrors ?? [];
    if (errores.length === 0 || !errores.every((e) => e.code === 11000)) throw error;
    omitidos[nombre] = (omitidos[nombre] ?? 0) + errores.length;
  }
}

async function agrupar(coleccion, campo, filtro = { datosDeDemostracion: true }) {
  return coleccion.aggregate([{ $match: filtro }, { $group: { _id: `$${campo}`, n: { $sum: 1 } } }, { $sort: { n: -1 } }]).toArray();
}
const linea = (filas) => filas.map((f) => `${f._id}=${f.n}`).join(' · ');

async function main() {
  const cliente = new MongoClient(MONGODB_URI);
  try {
    await cliente.connect();
    const db = cliente.db(DB_NAME);
    const [usuarios, tokens, auditoria, suscripciones, sectores] =
      [...COLECCIONES_SEMBRADAS, 'sectores'].map((nombre) => db.collection(nombre));
    await usuarios.createIndex({ correo: 1 }, { unique: true });

    const listaSectores = await sectores.find({}, { projection: { slug: 1, poblacion: 1 } }).toArray();
    if (listaSectores.length === 0) {
      console.error('No hay sectores en la base: el barrio de cada cuenta es uno de ellos. Corre primero sembrar-sectores.mjs.');
      process.exit(1);
    }
    cargarBarrios(listaSectores);

    const adminDoc = await usuarios.findOne({ rol: 'ADMIN' }, { projection: { correo: 1 } });
    if (!adminDoc) {
      console.warn('AVISO: no hay ninguna cuenta ADMIN. Para poder entrar al panel, arranca el backend con ADMIN_INICIAL_CORREO y '
        + 'VEEDOR_PASSWORD_HASH ANTES de sembrar (el ADMIN solo se crea si no existe ninguna cuenta). '
        + 'La auditoría sembrada quedará sin autor en las acciones de administrador.');
    } else {
      admin = { id: adminDoc._id, correo: adminDoc.correo };
    }

    for (const [nombre, coleccion] of [['usuarios', usuarios], ['tokens_cuenta', tokens],
      ['auditoria_cuentas', auditoria], ['suscripciones', suscripciones]]) {
      const previas = await coleccion.deleteMany({ datosDeDemostracion: true });
      if (previas.deletedCount > 0) console.log(`Retirados ${previas.deletedCount} documentos de demostración de '${nombre}' de una siembra anterior.`);
    }

    // Los correos de las cuentas reales que ya existan no pueden repetirse.
    for await (const existente of usuarios.find({}, { projection: { correo: 1 } })) usados.add(existente.correo);

    const LOTE = 1000;
    let insertadas = 0;
    for (let inicio = 0; inicio < CANTIDAD; inicio += LOTE) {
      const generadas = Array.from({ length: Math.min(LOTE, CANTIDAD - inicio) }, crearCuenta);
      await Promise.all([
        escribir('usuarios', usuarios, generadas.map((g) => g.usuario)),
        escribir('tokens_cuenta', tokens, generadas.flatMap((g) => g.tokens)),
        escribir('auditoria_cuentas', auditoria, generadas.flatMap((g) => g.auditoria)),
        escribir('suscripciones', suscripciones, generadas.flatMap((g) => g.suscripciones)),
      ]);
      insertadas += generadas.length;
      process.stdout.write(`\rCuentas insertadas ${insertadas} / ${CANTIDAD}`);
    }
    console.log('\n');

    const demo = { datosDeDemostracion: true };
    const totalUsuarios = await usuarios.countDocuments({});
    const slugs = new Set(listaSectores.map((s) => s.slug));
    const porBarrio = await agrupar(usuarios, 'barrio');
    const sinBarrio = porBarrio.filter((f) => f._id == null || f._id === '').reduce((suma, f) => suma + f.n, 0);
    const barriosInexistentes = porBarrio.filter((f) => f._id != null && !slugs.has(f._id));
    const conSegundoFactor = await usuarios.countDocuments({ ...demo, secretoTotp: { $ne: null } });
    const distintos = await usuarios.aggregate([{ $match: demo }, { $group: { _id: '$nombre' } }, { $count: 'n' }]).toArray();

    console.log('Conteo por colección (de demostración / total):');
    for (const [nombre, coleccion] of [['usuarios', usuarios], ['tokens_cuenta', tokens],
      ['auditoria_cuentas', auditoria], ['suscripciones', suscripciones]]) {
      console.log(`  ${nombre.padEnd(18)} ${String(await coleccion.countDocuments(demo)).padStart(7)} / ${await coleccion.countDocuments({})}`);
    }
    console.log(`\nNombres completos distintos: ${distintos[0]?.n ?? 0}`);
    console.log(`Por estado:  ${linea(await agrupar(usuarios, 'estado'))}`);
    console.log(`Por rol:     ${linea(await agrupar(usuarios, 'rol'))}`);
    console.log(`Por dominio: ${linea((await usuarios.aggregate([{ $match: demo }, { $group: { _id: { $arrayElemAt: [{ $split: ['$correo', '@'] }, 1] }, n: { $sum: 1 } } }, { $sort: { n: -1 } }]).toArray()))}`);
    console.log(`Barrios: ${porBarrio.length} distintos de ${slugs.size} · sin barrio: ${sinBarrio} · barrio inexistente: ${barriosInexistentes.length}`);
    console.log(`Los cinco con más cuentas: ${linea(porBarrio.slice(0, 5))}`);
    console.log(`Con segundo factor (TOTP): ${conSegundoFactor}`);
    console.log(`Tokens por tipo:        ${linea(await agrupar(tokens, 'tipo'))}`);
    console.log(`Auditoría por acción:   ${linea(await agrupar(auditoria, 'accion'))}`);
    console.log(`Suscripciones por estado: ${linea(await agrupar(suscripciones, 'estado'))}`);
    console.log('\nMuestra:');
    for (const c of await usuarios.find(demo).limit(6).toArray()) {
      console.log(`  ${c.nombre.padEnd(34)} ${c.correo.padEnd(38)} ${c.rol.padEnd(11)} ${c.estado.padEnd(22)} ${c.barrio}`);
    }
    console.log('\nClave de demostración de las cuentas ACTIVAS: DemoAguaVigia-2026');
    if (Object.keys(omitidos).length > 0) {
      console.log('Omitidos porque la aplicación ya había modificado esas filas (perdieron la marca de demostración): '
        + linea(Object.entries(omitidos).map(([_id, n]) => ({ _id, n }))));
    }

    const fallos = [];
    if (totalUsuarios < MINIMO) fallos.push(`'usuarios' tiene ${totalUsuarios} documentos y se exigen al menos ${MINIMO}`);
    if (sinBarrio > 0) fallos.push(`${sinBarrio} cuentas sembradas quedaron sin barrio`);
    if (barriosInexistentes.length > 0) fallos.push(`hay cuentas con un barrio que no existe en 'sectores': ${barriosInexistentes.slice(0, 3).map((f) => f._id).join(', ')}`);
    if (fallos.length > 0) {
      console.error(`\nFALLA la comprobación final:\n  - ${fallos.join('\n  - ')}`);
      process.exitCode = 1;
    } else {
      console.log(`\nComprobación final: OK (${totalUsuarios} cuentas en 'usuarios', mínimo exigido ${MINIMO}).`);
    }
  } finally {
    await cliente.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
