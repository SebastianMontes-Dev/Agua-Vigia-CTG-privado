#!/usr/bin/env node
// Verifica que la base quedó como la deja un `docker compose up` (ADR-094): los 211 barrios, las cuentas sintéticas que crea el
// backend con barrio válido y sin nada fingido, el ADMIN inicial y ninguna cuenta de panel sintética. Muestra además cuántos
// documentos tiene cada colección. Sale con error si algo falla.
//
//   docker compose run --rm sembrador verificar          (sin Node en el equipo)
//   node scripts/verificar-datos.mjs                      (desde el equipo, contra localhost:27017)
//
// Las cuentas sintéticas son lo único inventado del sistema: se verifica que sean exactamente eso (vecinos activos, marcados como
// demostración, con barrio del catastro y sin consentimiento ni verificación de barrio que nadie dio). VECINOS_SINTETICOS es la
// cantidad que debe haber; con 0 (la siembra desactivada) no se exige ninguna.
// El contrato de los campos que lee este script lo guarda UsuarioMongoAdapterTest.elDocumentoSinteticoTieneLosCamposQueVerificarDatosEspera.

import { MongoClient } from 'mongodb';

const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';
const SECTORES_ESPERADOS = 211;
const CUENTAS_SINTETICAS = Number(process.env.VECINOS_SINTETICOS ?? process.env.MINIMO_USUARIOS ?? 30000);
const fmt = (n) => n.toLocaleString('es-CO');

const cliente = new MongoClient(MONGODB_URI, { serverSelectionTimeoutMS: 5000 });
try {
  await cliente.connect();
  const db = cliente.db(DB_NAME);
  const usuarios = db.collection('usuarios');
  const nombres = (await db.listCollections({}, { nameOnly: true }).toArray()).map((c) => c.name).sort();

  console.log(`Base '${DB_NAME}' en ${MONGODB_URI.replace(/\/\/.*@/, '//***@')}\n`);
  console.log(`${'colección'.padEnd(26)} ${'documentos'.padStart(11)}`);
  let total = 0;
  for (const nombre of nombres) {
    const n = await db.collection(nombre).countDocuments();
    total += n;
    console.log(`${nombre.padEnd(26)} ${fmt(n).padStart(11)}`);
  }
  console.log(`${'─'.repeat(26)} ${'─'.repeat(11)}`);
  console.log(`${'total'.padEnd(26)} ${fmt(total).padStart(11)}\n`);

  const fallos = [];
  const comprobar = (descripcion, cumple, detalle) => {
    console.log(`${cumple ? 'OK    ' : 'FALLA '} ${descripcion}${detalle ? ` (${detalle})` : ''}`);
    if (!cumple) fallos.push(`${descripcion}${detalle ? `: ${detalle}` : ''}`);
  };

  const sectores = await db.collection('sectores').distinct('slug');
  comprobar(`${SECTORES_ESPERADOS} sectores`, sectores.length === SECTORES_ESPERADOS, `hay ${sectores.length}`);

  const sinteticas = await usuarios.countDocuments({ datosDeDemostracion: true });
  comprobar(`${fmt(CUENTAS_SINTETICAS)} cuentas sintéticas`, sinteticas >= CUENTAS_SINTETICAS, `hay ${fmt(sinteticas)}`);

  const marcadas = { datosDeDemostracion: true };
  const conBarrioInvalido = await usuarios.countDocuments({ ...marcadas, barrio: { $nin: sectores } });
  comprobar('toda cuenta sintética tiene un barrio que existe en el catastro', conBarrioInvalido === 0, `${fmt(conBarrioInvalido)} sin barrio válido`);

  const noSonVecinosActivos = await usuarios.countDocuments({ ...marcadas, $or: [{ rol: { $ne: 'VECINO' } }, { estado: { $ne: 'ACTIVA' } }] });
  comprobar('toda cuenta sintética es un vecino activo (ninguna de panel)', noSonVecinosActivos === 0, `${fmt(noSonVecinosActivos)} distintas`);

  const sinOrigen = await usuarios.countDocuments({ ...marcadas, origen: { $ne: 'SEMBRADO' } });
  comprobar('toda cuenta sintética declara origen SEMBRADO', sinOrigen === 0, `${fmt(sinOrigen)} sin él`);

  const fingidas = await usuarios.countDocuments({
    ...marcadas,
    $or: [{ barrioVerificado: true }, { 'consentimientos.0': { $exists: true } }],
  });
  comprobar('ninguna cuenta sintética finge barrio verificado ni consentimiento', fingidas === 0, `${fmt(fingidas)} lo fingen`);

  const conCorreoReal = await usuarios.countDocuments({ ...marcadas, correo: { $not: /\.invalid$/ } });
  comprobar('el correo de toda cuenta sintética es de un dominio reservado (.invalid)', conCorreoReal === 0, `${fmt(conCorreoReal)} no`);

  const admins = await usuarios.countDocuments({ rol: 'ADMIN', estado: 'ACTIVA' });
  comprobar('hay al menos un ADMIN activo', admins >= 1, `hay ${admins}`);

  const real = await usuarios.countDocuments({ datosDeDemostracion: { $ne: true } });
  console.log(`\n${fmt(sinteticas)} cuentas son sintéticas generadas por el sistema con las reglas de alta de un vecino; ${fmt(real)} no lo son.`);

  if (fallos.length > 0) {
    console.error(`\nFALLA:\n  - ${fallos.join('\n  - ')}`);
    process.exitCode = 1;
  } else {
    console.log('\nVerificación de los datos: OK');
  }
} finally {
  await cliente.close();
}
