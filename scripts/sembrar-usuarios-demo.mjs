#!/usr/bin/env node
// Siembra cuentas de demostración en la colección `usuarios` para presentar el proyecto con una base
// grande (por defecto 20 000) y variada: nombres y apellidos distintos, correos con estilos y proveedores
// distintos, los seis estados de cuenta, los roles OBSERVADOR y VEEDOR, permisos sueltos y fechas repartidas
// en los últimos 18 meses.
//
// Uso:
//   cd scripts && npm install
//   node sembrar-usuarios-demo.mjs                       # 20 000 cuentas
//   node sembrar-usuarios-demo.mjs --cantidad 5000 --semilla 7
//
// Variables: MONGODB_URI (por defecto mongodb://localhost:27017) y MONGODB_DB (por defecto aguavigia).
//
// IMPORTANTE — orden:
//   1. Arranca el backend con ADMIN_INICIAL_CORREO y VEEDOR_PASSWORD_HASH para que cree al ADMIN. Ese ADMIN solo
//      se crea si NO existe ninguna cuenta: si siembras estas antes, nunca se crea.
//   2. Después corre este script.
//
// Es determinista (misma semilla, mismas cuentas) e idempotente: antes de insertar borra únicamente las cuentas
// que él mismo sembró (marca `datosDeDemostracion: true`); jamás toca cuentas reales ni al ADMIN. Si la aplicación
// vuelve a guardar una cuenta de demostración (por ejemplo, un ADMIN la suspende), pierde esa marca y deja de
// contarse como sembrada.
//
// Las cuentas ACTIVAS comparten una clave de demostración, DemoAguaVigia-2026 (solo para entrar a probar como un
// veedor u observador; no hay ADMIN entre ellas). Por eso el script se niega a correr contra una base que no sea
// local, salvo que pases --permitir-remoto. Los correos usan dominios reales de proveedores como los genera
// cualquier dato de prueba: no los uses con un SMTP real (el compose de desarrollo envía a Mailhog).

import { MongoClient } from 'mongodb';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
  options: {
    cantidad: { type: 'string', default: '20000' },
    semilla: { type: 'string', default: '2026' },
    'permitir-remoto': { type: 'boolean', default: false },
  },
});
const CANTIDAD = Number(values.cantidad);
const SEMILLA = Number(values.semilla);
const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';

// BCrypt (coste 10) de «DemoAguaVigia-2026», calculado con la misma biblioteca que usa el backend.
const HASH_CLAVE_DEMO = '$2a$10$9Z7IzVuDwklPgBYmu8noLeYWAUyXtjYWkfdBCYi282zRzSANE/256';
const CLASE = 'com.aguavigia.ctg.infrastructure.persistence.mongo.UsuarioDocumento';

if (!Number.isInteger(CANTIDAD) || CANTIDAD < 1 || CANTIDAD > 500000) {
  console.error('--cantidad debe ser un entero entre 1 y 500000');
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

const usados = new Set();
const nombresUsados = new Set();
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

  const creadoEn = fechaDeCreacion();
  const margen = Math.max(0, Math.min(AHORA - creadoEn.getTime(), 150 * DIA));
  const actualizadoEn = estado === 'PENDIENTE_VERIFICACION' ? creadoEn : new Date(creadoEn.getTime() + Math.floor(azar() * margen));

  return {
    _id: uuid(),
    correo,
    nombre: `${nombre} ${apellido1} ${apellido2}`,
    claveHash: estado === 'INVITADA' ? null : HASH_CLAVE_DEMO,
    estado,
    rol,
    permisosConcedidos: concedidos,
    permisosRevocados: revocados,
    secretoTotp: null,
    segundoFactorConfirmadoEn: null,
    creadoEn,
    actualizadoEn,
    datosDeDemostracion: true,
    _class: CLASE,
  };
}

async function main() {
  const cliente = new MongoClient(MONGODB_URI);
  try {
    await cliente.connect();
    const coleccion = cliente.db(DB_NAME).collection('usuarios');
    await coleccion.createIndex({ correo: 1 }, { unique: true });

    const admins = await coleccion.countDocuments({ rol: 'ADMIN' });
    if (admins === 0) {
      console.warn('AVISO: no hay ninguna cuenta ADMIN. Para poder entrar al panel, arranca el backend con ADMIN_INICIAL_CORREO y '
        + 'VEEDOR_PASSWORD_HASH ANTES de sembrar (el ADMIN solo se crea si no existe ninguna cuenta).');
    }

    const previas = await coleccion.deleteMany({ datosDeDemostracion: true });
    if (previas.deletedCount > 0) console.log(`Retiradas ${previas.deletedCount} cuentas de demostración de una siembra anterior.`);

    // Los correos de las cuentas reales que ya existan no pueden repetirse.
    for await (const existente of coleccion.find({}, { projection: { correo: 1 } })) usados.add(existente.correo);

    const LOTE = 1000;
    let insertadas = 0;
    for (let inicio = 0; inicio < CANTIDAD; inicio += LOTE) {
      const lote = Array.from({ length: Math.min(LOTE, CANTIDAD - inicio) }, crearCuenta);
      await coleccion.insertMany(lote, { ordered: false });
      insertadas += lote.length;
      process.stdout.write(`\rInsertadas ${insertadas} / ${CANTIDAD}`);
    }
    console.log('\n');

    const porEstado = await coleccion.aggregate([{ $match: { datosDeDemostracion: true } }, { $group: { _id: '$estado', n: { $sum: 1 } } }, { $sort: { n: -1 } }]).toArray();
    const porRol = await coleccion.aggregate([{ $match: { datosDeDemostracion: true } }, { $group: { _id: '$rol', n: { $sum: 1 } } }, { $sort: { n: -1 } }]).toArray();
    const porDominio = await coleccion.aggregate([{ $match: { datosDeDemostracion: true } }, { $group: { _id: { $arrayElemAt: [{ $split: ['$correo', '@'] }, 1] }, n: { $sum: 1 } } }, { $sort: { n: -1 } }]).toArray();
    const distintos = await coleccion.aggregate([{ $match: { datosDeDemostracion: true } }, { $group: { _id: '$nombre' } }, { $count: 'n' }]).toArray();
    const total = await coleccion.countDocuments({});

    const linea = (filas) => filas.map((f) => `${f._id}=${f.n}`).join(' · ');
    console.log(`Cuentas de demostración: ${insertadas} (total en la colección: ${total})`);
    console.log(`Nombres completos distintos: ${distintos[0]?.n ?? 0}`);
    console.log(`Por estado:  ${linea(porEstado)}`);
    console.log(`Por rol:     ${linea(porRol)}`);
    console.log(`Por dominio: ${linea(porDominio)}`);
    console.log('\nMuestra:');
    for (const c of await coleccion.find({ datosDeDemostracion: true }).limit(6).toArray()) {
      console.log(`  ${c.nombre.padEnd(34)} ${c.correo.padEnd(38)} ${c.rol.padEnd(11)} ${c.estado}`);
    }
    console.log('\nClave de demostración de las cuentas ACTIVAS: DemoAguaVigia-2026');
  } finally {
    await cliente.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
