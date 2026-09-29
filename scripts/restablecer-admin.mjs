#!/usr/bin/env node
// Recupera el acceso de un ADMIN que perdió su segundo factor (TOTP) o su clave, en la base LOCAL. No hay una ruta
// de la API para esto a propósito: un ADMIN no puede desactivar su propio TOTP y nadie puede quitárselo a otro, así
// que sin este script solo quedaba editar Mongo a mano.
//
// Uso:
//   node scripts/restablecer-admin.mjs --correo veedor@aguavigia.local              # solo borra el TOTP
//   node scripts/restablecer-admin.mjs --correo veedor@aguavigia.local --clave-del-env
//
// --clave-del-env fija como clave la de VEEDOR_PASSWORD_HASH (variable de entorno o `.env` de la raíz): el hash
// BCrypt de una clave que tú elegiste (docs/ingenieria/entorno-local.md §4). El script nunca ve la clave en claro.
//
// Tras correrlo, la próxima sesión del ADMIN solo sirve para dar de alta un TOTP nuevo, igual que la primera. Queda
// un evento en `auditoria_cuentas`. Variables: MONGODB_URI (por defecto mongodb://localhost:27017/?directConnection=true)
// y MONGODB_DB (por defecto aguavigia). Se niega a correr contra una base que no sea local.

import { MongoClient } from 'mongodb';
import { parseArgs } from 'node:util';
import { randomUUID } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';

const { values } = parseArgs({
  options: {
    correo: { type: 'string' },
    'clave-del-env': { type: 'boolean', default: false },
  },
});
const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';
const CLASE_AUDITORIA = 'com.aguavigia.ctg.infrastructure.persistence.mongo.EventoAuditoriaDocumento';
const HASH_BCRYPT = /^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$/;

function falla(mensaje) {
  console.error(mensaje);
  process.exit(1);
}

function hashDelEntorno() {
  let hash = process.env.VEEDOR_PASSWORD_HASH;
  if (!hash && existsSync('.env')) {
    const linea = readFileSync('.env', 'utf8').split(/\r?\n/).find((l) => l.startsWith('VEEDOR_PASSWORD_HASH='));
    // En el .env cada `$` va escapado como `$$` para docker compose.
    hash = linea?.slice('VEEDOR_PASSWORD_HASH='.length).trim().replaceAll('$$', '$');
  }
  if (!hash || !HASH_BCRYPT.test(hash)) {
    falla('VEEDOR_PASSWORD_HASH no está o no es un hash BCrypt. Genéralo con GenerarHashVeedor (entorno-local.md §4).');
  }
  return hash;
}

if (!values.correo) falla('Falta --correo del ADMIN a restablecer.');
if (!/^mongodb:\/\/(localhost|127\.0\.0\.1|\[::1\])([:/]|$)/.test(MONGODB_URI)) {
  falla('Me niego a tocar una base que no es local.');
}

const correo = values.correo.trim().toLowerCase();
const nuevaClave = values['clave-del-env'] ? hashDelEntorno() : null;
const cliente = new MongoClient(MONGODB_URI);
try {
  await cliente.connect();
  const base = cliente.db(DB_NAME);
  const usuarios = base.collection('usuarios');
  const cuenta = await usuarios.findOne({ correo });
  if (!cuenta) falla(`No hay ninguna cuenta con el correo ${correo}.`);
  if (cuenta.rol !== 'ADMIN') falla(`${correo} no es ADMIN (es ${cuenta.rol}): este script solo recupera administradores.`);

  const ahora = new Date();
  const cambios = { secretoTotp: null, segundoFactorConfirmadoEn: null, actualizadoEn: ahora };
  if (nuevaClave) cambios.claveHash = nuevaClave;
  await usuarios.updateOne({ _id: cuenta._id }, { $set: cambios });

  const evento = (accion, detalle) => ({
    _id: randomUUID(), accion, autorId: null, autorCorreo: null, sujetoId: cuenta._id, sujetoCorreo: correo,
    detalle, ip: 'sistema', ocurrioEn: ahora, _class: CLASE_AUDITORIA,
  });
  const eventos = [evento('SEGUNDO_FACTOR_DESACTIVADO', 'Restablecido a mano con scripts/restablecer-admin.mjs')];
  if (nuevaClave) eventos.push(evento('CLAVE_RESTABLECIDA', 'Clave fijada desde VEEDOR_PASSWORD_HASH con scripts/restablecer-admin.mjs'));
  await base.collection('auditoria_cuentas').insertMany(eventos);

  console.log(`${correo}: segundo factor borrado${nuevaClave ? ' y clave nueva fijada' : ''}.`);
  console.log('La próxima sesión solo sirve para dar de alta un TOTP nuevo. Si la cuenta quedó bloqueada por intentos'
    + ' fallidos (423), espera a que venza el bloqueo.');
} finally {
  await cliente.close();
}
