#!/usr/bin/env node
// Agrega usuarios NUEVOS a la base en vivo, distintos en cada ejecución (faker, sin semilla fija), para mostrar dónde se
// guardan y cómo se comporta el sistema cuando crece (ADR-087). Cada ejecución es un «lote» con nombre, que se puede
// contar y borrar sin tocar nada más.
//
// Sin instalar Node en el equipo (dentro de la red del compose):
//   docker compose run --rm sembrador agregar-usuarios --cantidad 1000
//   docker compose run --rm sembrador agregar-usuarios --cantidad 200 --modo api
//   docker compose run --rm sembrador agregar-usuarios --borrar-lote lote-20260929-1715
//
// Modos:
//   directo (por defecto)  Inserta en Mongo cuentas completas, igual que las 30 000 iniciales (usuario, tokens, auditoría,
//                          suscripciones), con la marca `lote`. Miles por segundo.
//   api                    Cada usuario se registra de verdad con POST /api/cuentas/registro: el backend valida, cifra la
//                          clave con BCrypt, audita y envía el correo de verificación (a MailHog). Queda en
//                          PENDIENTE_VERIFICACION. Es más lento a propósito (RNF024) y el límite es de 10 registros cada
//                          10 min por IP, salvo con el perfil `carga` (docker-compose.carga.yml), que lo quita.
//
// Variables: MONGODB_URI, MONGODB_DB y API_URL (el compose ya las pone).

import { MongoClient } from 'mongodb';
import { parseArgs } from 'node:util';
import { CLAVE_DEMO, COLECCIONES_SEMBRADAS, crearFabricaDeCuentas, sinAcentos } from './lib/cuentas-demo.mjs';
import { crearFuenteFaker } from './lib/fuente-faker.mjs';

const { values } = parseArgs({
  options: {
    cantidad: { type: 'string', default: '1000' },
    modo: { type: 'string', default: 'directo' },
    lote: { type: 'string' },
    semilla: { type: 'string' },
    concurrencia: { type: 'string', default: '20' },
    'borrar-lote': { type: 'string' },
    'permitir-remoto': { type: 'boolean', default: false },
  },
});

const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';
const API = (process.env.API_URL ?? 'http://localhost:8081').replace(/\/$/, '');
const CANTIDAD = Number(values.cantidad);
const MODO = values.modo;
const CONCURRENCIA = Number(values.concurrencia);
const marcaDeTiempo = new Date().toISOString().slice(0, 16).replace(/[-:]/g, '').replace('T', '-');
const LOTE = values.lote ?? `lote-${marcaDeTiempo}`;
// Las cuentas del modo api no llevan campos propios (las crea el backend): se reconocen por este dominio reservado.
const dominioDelLote = (lote) => `${sinAcentos(lote)}.registro.aguavigia.local`;

if (!/^mongodb:\/\/(localhost|127\.0\.0\.1|\[::1\]|mongo)([:/]|$)/.test(MONGODB_URI) && !values['permitir-remoto']) {
  console.error('Me niego a escribir cuentas de demostración en una base que no es local. Repite con --permitir-remoto.');
  process.exit(1);
}
if (!values['borrar-lote']) {
  if (!Number.isInteger(CANTIDAD) || CANTIDAD < 1 || CANTIDAD > 200000) {
    console.error('--cantidad debe ser un entero entre 1 y 200000');
    process.exit(1);
  }
  if (!['directo', 'api'].includes(MODO)) {
    console.error('--modo debe ser «directo» o «api»');
    process.exit(1);
  }
}

const { azar, nombrar } = crearFuenteFaker({ semilla: values.semilla });

const formato = (n) => n.toLocaleString('es-CO');

async function conteoDe(db) {
  return Object.fromEntries(await Promise.all(COLECCIONES_SEMBRADAS.map(async (c) => [c, await db.collection(c).countDocuments()])));
}

function imprimirAntesYDespues(antes, despues) {
  console.log(`\nBase '${DB_NAME}' — antes → después:`);
  for (const c of COLECCIONES_SEMBRADAS) {
    const diferencia = despues[c] - antes[c];
    console.log(`  ${`${DB_NAME}.${c}`.padEnd(30)} ${formato(antes[c]).padStart(8)} → ${formato(despues[c]).padStart(8)}  (${diferencia >= 0 ? '+' : ''}${formato(diferencia)})`);
  }
}

async function modoDirecto(db) {
  const usuarios = db.collection('usuarios');
  const sectores = await db.collection('sectores').find({}, { projection: { slug: 1, poblacion: 1 } }).toArray();
  if (sectores.length === 0) throw new Error('No hay sectores: levanta el proyecto con docker compose up (el sembrador los crea).');

  const fabrica = crearFabricaDeCuentas({ azar, nombrar, extras: { lote: LOTE } });
  fabrica.cargarBarrios(sectores);
  const admin = await usuarios.findOne({ rol: 'ADMIN' }, { projection: { correo: 1 } });
  if (admin) fabrica.fijarAdmin({ id: admin._id, correo: admin.correo });
  // Ningún correo puede repetir uno que ya exista: se cargan todos antes de generar.
  for await (const { correo } of usuarios.find({}, { projection: { correo: 1 } })) fabrica.correosUsados.add(correo);

  let insertadas = 0;
  let reintentos = 0;
  while (insertadas < CANTIDAD) {
    const generadas = Array.from({ length: Math.min(1000, CANTIDAD - insertadas) }, fabrica.crearCuenta);
    const ids = generadas.map((g) => g.usuario._id);
    const huboChoques = await usuarios.insertMany(generadas.map((g) => g.usuario), { ordered: false }).then(() => false, (error) => {
      if (!(error.writeErrors ?? []).every((e) => e.code === 11000)) throw error;
      return true;
    });
    // Solo se escriben tokens, auditoría y suscripciones de las cuentas que sí entraron (un correo que otro proceso
    // insertó entre medias choca con el índice único y se vuelve a generar en la siguiente vuelta).
    const entraron = huboChoques
      ? new Set((await usuarios.find({ _id: { $in: ids } }, { projection: { _id: 1 } }).toArray()).map((u) => String(u._id)))
      : new Set(ids.map(String));
    const buenas = generadas.filter((g) => entraron.has(String(g.usuario._id)));
    reintentos += generadas.length - buenas.length;
    await Promise.all([
      ['tokens_cuenta', buenas.flatMap((g) => g.tokens)],
      ['auditoria_cuentas', buenas.flatMap((g) => g.auditoria)],
      ['suscripciones', buenas.flatMap((g) => g.suscripciones)],
    ].filter(([, docs]) => docs.length > 0).map(([c, docs]) => db.collection(c).insertMany(docs, { ordered: false })));
    insertadas += buenas.length;
    process.stdout.write(`\rCuentas insertadas ${formato(insertadas)} / ${formato(CANTIDAD)}`);
  }
  console.log(reintentos > 0 ? `\n(${reintentos} correos chocaron con otros ya existentes y se generaron de nuevo)` : '');
  return { filtro: { lote: LOTE } };
}

async function modoApi(db) {
  const respuesta = await fetch(`${API}/api/sectores`).catch(() => null);
  if (!respuesta?.ok) throw new Error(`No respondió ${API}/api/sectores: ¿está el backend arriba?`);
  const { sectores } = await respuesta.json();
  const dominio = dominioDelLote(LOTE);
  const estados = {};
  let enviados = 0;
  let siguiente = 0;
  const inicio = Date.now();

  async function trabajador() {
    while (siguiente < CANTIDAD) {
      const i = siguiente++;
      const { nombre, apellido1, apellido2 } = nombrar();
      const local = `${sinAcentos(nombre.split(' ')[0])}.${sinAcentos(apellido1)}.${i}`;
      const cuerpo = {
        correo: `${local}@${dominio}`,
        nombre: `${nombre} ${apellido1} ${apellido2}`.slice(0, 80),
        clave: CLAVE_DEMO,
        barrioId: sectores[Math.floor(azar() * sectores.length)].id,
      };
      const r = await fetch(`${API}/api/cuentas/registro`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(cuerpo),
      }).catch(() => ({ status: 'error de red' }));
      estados[r.status] = (estados[r.status] ?? 0) + 1;
      enviados++;
      if (enviados % 25 === 0 || enviados === CANTIDAD) {
        process.stdout.write(`\rRegistros enviados ${formato(enviados)} / ${formato(CANTIDAD)}  ${JSON.stringify(estados)}`);
      }
    }
  }
  await Promise.all(Array.from({ length: Math.max(1, CONCURRENCIA) }, trabajador));
  const segundos = (Date.now() - inicio) / 1000;
  console.log(`\n${formato(enviados)} registros en ${segundos.toFixed(1)} s (${(enviados / segundos).toFixed(1)} por segundo). Respuestas: ${JSON.stringify(estados)}`);
  if (estados[429]) {
    console.log('Los 429 son el límite por IP de /api/cuentas/** (10 cada 10 min). Para registrar miles, levanta el backend con\n'
      + 'el perfil de carga: docker compose -f docker-compose.yml -f docker-compose.carga.yml up -d backend');
  }
  // El backend registra en segundo plano con una duración mínima: se espera a que aparezcan las cuentas.
  const filtro = { correo: { $regex: `@${dominio.replace(/\./g, '\\.')}$` } };
  const esperadas = estados[202] ?? 0;
  for (let i = 0; i < 20 && await db.collection('usuarios').countDocuments(filtro) < esperadas; i++) {
    await new Promise((resolver) => setTimeout(resolver, 500));
  }
  return { filtro };
}

async function borrarLote(db, lote) {
  const porCampo = { lote };
  const porDominio = { correo: { $regex: `@${dominioDelLote(lote).replace(/\./g, '\\.')}$` } };
  const ids = (await db.collection('usuarios').find({ $or: [porCampo, porDominio] }, { projection: { _id: 1 } }).toArray()).map((u) => u._id);
  const antes = await conteoDe(db);
  await db.collection('usuarios').deleteMany({ _id: { $in: ids } });
  await db.collection('tokens_cuenta').deleteMany({ $or: [porCampo, { usuarioId: { $in: ids } }] });
  await db.collection('auditoria_cuentas').deleteMany({ $or: [porCampo, { sujetoId: { $in: ids } }] });
  await db.collection('suscripciones').deleteMany(porCampo);
  imprimirAntesYDespues(antes, await conteoDe(db));
  console.log(`\nLote '${lote}' borrado: ${formato(ids.length)} cuentas y lo que dejaron.`);
}

const cliente = new MongoClient(MONGODB_URI, { serverSelectionTimeoutMS: 5000 });
try {
  await cliente.connect();
  const db = cliente.db(DB_NAME);
  if (values['borrar-lote']) {
    await borrarLote(db, values['borrar-lote']);
  } else {
    console.log(`Lote '${LOTE}': ${formato(CANTIDAD)} usuarios nuevos en modo ${MODO}.\n`);
    const antes = await conteoDe(db);
    const { filtro } = MODO === 'api' ? await modoApi(db) : await modoDirecto(db);
    imprimirAntesYDespues(antes, await conteoDe(db));

    const delLote = await db.collection('usuarios').countDocuments(filtro);
    console.log(`\nDel lote hay ${formato(delLote)} cuentas en ${DB_NAME}.usuarios. Tres de ellas, tal como están guardadas:`);
    const muestra = await db.collection('usuarios').find(filtro, { projection: { claveHash: 0, secretoTotp: 0, _class: 0 } }).limit(3).toArray();
    for (const doc of muestra) console.log(JSON.stringify(doc, null, 2));
    console.log(`\nPara verlas en Mongo:   db.usuarios.find(${JSON.stringify(filtro)})`);
    console.log(`Para quitar este lote:  docker compose run --rm sembrador agregar-usuarios --borrar-lote ${LOTE}`);
  }
} catch (error) {
  console.error(`\n${error.message}`);
  process.exitCode = 1;
} finally {
  await cliente.close();
}
