#!/usr/bin/env node
// Muestra cuántos documentos tiene cada colección de Mongo y si se cumplen los mínimos de la entrega (≥ 30 000 usuarios,
// 211 sectores). Sale con error si alguno falla.
//
//   docker compose run --rm sembrador verificar          (sin Node en el equipo)
//   node scripts/verificar-datos.mjs                      (desde el equipo, contra localhost:27017)

import { MongoClient } from 'mongodb';

const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';
const MINIMOS = { usuarios: Number(process.env.MINIMO_USUARIOS ?? 30000), sectores: 211 };

const cliente = new MongoClient(MONGODB_URI, { serverSelectionTimeoutMS: 5000 });
try {
  await cliente.connect();
  const db = cliente.db(DB_NAME);
  const nombres = (await db.listCollections({}, { nameOnly: true }).toArray()).map((c) => c.name).sort();

  console.log(`Base '${DB_NAME}' en ${MONGODB_URI.replace(/\/\/.*@/, '//***@')}\n`);
  console.log(`${'colección'.padEnd(26)} ${'documentos'.padStart(11)}   mínimo`);
  let total = 0;
  const fallos = [];
  for (const nombre of nombres) {
    const n = await db.collection(nombre).countDocuments();
    total += n;
    const minimo = MINIMOS[nombre];
    const marca = minimo === undefined ? '' : n >= minimo ? `≥ ${minimo}  OK` : `≥ ${minimo}  FALTA`;
    if (minimo !== undefined && n < minimo) fallos.push(`${nombre}: ${n} < ${minimo}`);
    console.log(`${nombre.padEnd(26)} ${n.toLocaleString('es-CO').padStart(11)}   ${marca}`);
  }
  for (const nombre of Object.keys(MINIMOS).filter((m) => !nombres.includes(m))) fallos.push(`${nombre}: la colección no existe`);

  const demo = await db.collection('usuarios').countDocuments({ datosDeDemostracion: true });
  console.log(`${'─'.repeat(26)} ${'─'.repeat(11)}`);
  console.log(`${'total'.padEnd(26)} ${total.toLocaleString('es-CO').padStart(11)}`);
  console.log(`\nDe los usuarios, ${demo.toLocaleString('es-CO')} son cuentas de demostración sintéticas (marca datosDeDemostracion).`);

  if (fallos.length > 0) {
    console.error(`\nFALLA:\n  - ${fallos.join('\n  - ')}`);
    process.exitCode = 1;
  } else {
    console.log('\nMínimos de la entrega: OK');
  }
} finally {
  await cliente.close();
}
