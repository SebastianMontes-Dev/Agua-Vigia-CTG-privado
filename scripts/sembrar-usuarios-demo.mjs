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
import { parseArgs } from 'node:util';
import { CLAVE_DEMO, COLECCIONES_SEMBRADAS, crearFabricaDeCuentas } from './lib/cuentas-demo.mjs';

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

if (!Number.isInteger(CANTIDAD) || CANTIDAD < 1 || CANTIDAD > 500000) {
  console.error('--cantidad debe ser un entero entre 1 y 500000');
  process.exit(1);
}
if (!Number.isInteger(MINIMO) || MINIMO < 0) {
  console.error('--minimo debe ser un entero mayor o igual que 0');
  process.exit(1);
}
// `mongo` es el servicio de docker-compose.yml: el sembrador corre dentro de esa red (ADR-086).
if (!/^mongodb:\/\/(localhost|127\.0\.0\.1|\[::1\]|mongo)([:/]|$)/.test(MONGODB_URI) && !values['permitir-remoto']) {
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

// Nombres de la costa caribe colombiana; con la semilla fija, las 30 000 cuentas salen siempre iguales.
function nombrar({ elegir }) {
  const nombre = azar() < 0.22 ? `${elegir(NOMBRES)} ${elegir(NOMBRES)}` : elegir(NOMBRES);
  const apellido1 = elegir(APELLIDOS);
  let apellido2 = elegir(APELLIDOS);
  if (apellido2 === apellido1) apellido2 = elegir(APELLIDOS);
  return { nombre, apellido1, apellido2 };
}
const fabrica = crearFabricaDeCuentas({ azar, nombrar });
const { crearCuenta, cargarBarrios } = fabrica;
const usados = fabrica.correosUsados;

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
      fabrica.fijarAdmin({ id: adminDoc._id, correo: adminDoc.correo });
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
    console.log(`\nClave de demostración de las cuentas ACTIVAS: ${CLAVE_DEMO}`);
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
