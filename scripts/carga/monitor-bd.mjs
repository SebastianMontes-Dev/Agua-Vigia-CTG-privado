#!/usr/bin/env node
// Visor en vivo de la base mientras corre la demo de carga (ADR-088): cada segundo, cuántos documentos hay en las
// colecciones que la carga hace crecer y a qué ritmo. Se abre en otra terminal, al lado de la demo:
//
//   docker compose run --rm sembrador monitor              (sin Node en el equipo)
//   node scripts/carga/monitor-bd.mjs                      (con Node, contra localhost:27017)
//
// Opciones: --intervalo 1 (segundos) · --una-vez (una sola lectura y sale). Ctrl+C para salir.

import { MongoClient } from 'mongodb';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
  options: {
    intervalo: { type: 'string', default: '1' },
    'una-vez': { type: 'boolean', default: false },
  },
});
const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';
const INTERVALO_MS = Math.max(0.5, Number(values.intervalo)) * 1000;
const COLECCIONES = ['usuarios', 'reportes', 'eventos_bitacora', 'suscripciones', 'tokens_cuenta', 'auditoria_cuentas'];
// Las cuentas del registro masivo de la demo usan este dominio (flujo-ciudadano.js).
const DE_LA_CARGA = { correo: { $regex: '@carga\\.aguavigia\\.local$' } };
const formato = (n) => n.toLocaleString('es-CO');

const cliente = new MongoClient(MONGODB_URI, { serverSelectionTimeoutMS: 5000 });
await cliente.connect();
const db = cliente.db(DB_NAME);

async function leer() {
  // estimatedDocumentCount lee los metadatos de la colección: no compite con la carga como lo haría un conteo completo.
  const conteos = Object.fromEntries(await Promise.all(COLECCIONES.map(async (c) => [c, await db.collection(c).estimatedDocumentCount()])));
  conteos.registrosDeLaCarga = await db.collection('usuarios').countDocuments(DE_LA_CARGA);
  const barrios = await db.collection('sectores').aggregate([{ $group: { _id: '$estadoActual', n: { $sum: 1 } } }]).toArray();
  conteos.barrios = Object.fromEntries(barrios.map((b) => [b._id ?? 'sin datos', b.n]));
  return conteos;
}

function pintar(actual, inicial, anterior, segundos) {
  const filas = [
    `Base '${DB_NAME}' — ${new Date().toLocaleTimeString('es-CO')} · ${segundos.toFixed(0)} s mirando${values['una-vez'] ? '' : ' (Ctrl+C para salir)'}`,
    '',
    `${'colección'.padEnd(20)} ${'ahora'.padStart(10)} ${'desde el inicio'.padStart(16)} ${'por segundo'.padStart(12)}`,
  ];
  for (const c of [...COLECCIONES, 'registrosDeLaCarga']) {
    const nombre = c === 'registrosDeLaCarga' ? '  cuentas de la carga' : c;
    const porSegundo = anterior ? ((actual[c] - anterior[c]) * 1000) / INTERVALO_MS : 0;
    const desde = actual[c] - inicial[c];
    filas.push(`${nombre.padEnd(20)} ${formato(actual[c]).padStart(10)} ${`${desde >= 0 ? '+' : ''}${formato(desde)}`.padStart(16)} ${formato(Math.round(porSegundo)).padStart(12)}`);
  }
  filas.push('', `Barrios por estado: ${Object.entries(actual.barrios).map(([e, n]) => `${e} ${n}`).join(' · ')}`);
  // Borra la pantalla y vuelve arriba: así se lee como un tablero y no como un rollo de líneas.
  process.stdout.write(`\x1b[2J\x1b[H${filas.join('\n')}\n`);
}

const inicio = Date.now();
const inicial = await leer();
let anterior = null;
let actual = inicial;
pintar(actual, inicial, anterior, 0);
if (values['una-vez']) {
  await cliente.close();
} else {
  const detener = async () => { await cliente.close(); process.exit(0); };
  process.on('SIGINT', detener);
  process.on('SIGTERM', detener);
  for (;;) {
    await new Promise((resolver) => setTimeout(resolver, INTERVALO_MS));
    anterior = actual;
    actual = await leer();
    pintar(actual, inicial, anterior, (Date.now() - inicio) / 1000);
  }
}
