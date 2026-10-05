#!/usr/bin/env node
// Punto de entrada del servicio `sembrador` de docker-compose.yml (ADR-086, ADR-094): deja la base lista para presentar con un
// solo `docker compose up`, sin instalar Node en el equipo.
//
//   docker compose up                                   → corre `inicial` y termina
//   docker compose run --rm sembrador verificar         → conteo de cada colección contra los mínimos
//   docker compose run --rm sembrador totp <SECRETO>    → código de 6 dígitos del segundo factor (codigo-totp.mjs)
//   docker compose run --rm sembrador monitor           → la base en vivo durante la demo de carga (carga/monitor-bd.mjs)
//   docker compose run --rm sembrador <script> [args]   → cualquier otro script de scripts/
//
// `inicial` siembra solo lo que no puede ser real y no inventa nada del acueducto (ADR-094):
//   sectores   los 211 barrios del catastro, solo si hay menos de 211 (sembrar-sectores.mjs borra y vuelve a insertar)
//   cuentas    NO las siembra este script: las crea el propio backend, en segundo plano, con las mismas reglas de alta de
//              un vecino (ImportadorDeVecinosSinteticos, aguavigia.siembra.vecinos-sinteticos). Aquí solo se espera a que
//              terminen para decir cuántas hay.
// No hay reportes, cortes ni estados inventados: el mapa muestra lo que dicen Acuacar y los vecinos de verdad.
// sembrar-demo.mjs y sembrar-historico-cortes.mjs siguen en scripts/ para quien los pida a mano, pero no corren al arrancar.
// Repetir `docker compose up` no duplica nada: cada paso tiene su propia puerta.

import { spawn } from 'node:child_process';
import { MongoClient } from 'mongodb';

const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';
const SECTORES_ESPERADOS = 211;
// Cuántas cuentas sintéticas debe haber creado el backend (VECINOS_SINTETICOS en .env; 0 las desactiva).
const CUENTAS_SINTETICAS = Number(process.env.VECINOS_SINTETICOS ?? 30000);
const ESPERA_DE_CUENTAS_MS = Number(process.env.ESPERA_DE_CUENTAS_MS ?? 5 * 60 * 1000);
const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

function correr(script, args = []) {
  return new Promise((resolver, rechazar) => {
    const hijo = spawn(process.execPath, [script, ...args], { stdio: 'inherit' });
    hijo.on('exit', (codigo) => (codigo === 0 ? resolver() : rechazar(new Error(`${script} terminó con código ${codigo}`))));
  });
}

async function conectar() {
  for (let intento = 1; ; intento++) {
    const cliente = new MongoClient(MONGODB_URI, { serverSelectionTimeoutMS: 3000 });
    try {
      await cliente.connect();
      return cliente;
    } catch (error) {
      await cliente.close().catch(() => {});
      if (intento >= 20) throw error;
      await esperar(3000);
    }
  }
}

const contar = (db, coleccion, filtro = {}) => db.collection(coleccion).countDocuments(filtro);

// El backend las crea después del ADMIN inicial y de que existan los sectores, así que a veces hay que esperarlas.
async function esperarCuentas(db) {
  const limite = Date.now() + ESPERA_DE_CUENTAS_MS;
  let hay = await contar(db, 'usuarios', { datosDeDemostracion: true });
  while (hay < CUENTAS_SINTETICAS && Date.now() < limite) {
    await esperar(3000);
    hay = await contar(db, 'usuarios', { datosDeDemostracion: true });
  }
  return hay;
}

async function inicial() {
  const cliente = await conectar();
  try {
    const db = cliente.db(DB_NAME);

    const sectores = await contar(db, 'sectores');
    if (sectores < SECTORES_ESPERADOS) {
      console.log(`\n[1/2] Sectores: hay ${sectores}, se siembran los ${SECTORES_ESPERADOS} barrios de Cartagena.`);
      await correr('sembrar-sectores.mjs');
    } else {
      console.log(`\n[1/2] Sectores: ya están los ${sectores}.`);
    }

    if (CUENTAS_SINTETICAS > 0) {
      console.log(`\n[2/2] Cuentas sintéticas: las crea el backend (hasta ${CUENTAS_SINTETICAS}); se espera a que terminen.`);
      const hay = await esperarCuentas(db);
      if (hay < CUENTAS_SINTETICAS) {
        console.warn(`AVISO: hay ${hay} de ${CUENTAS_SINTETICAS} cuentas sintéticas y el backend sigue creándolas (o está apagado). `
          + 'Compruébalo con: docker compose logs backend');
      }
    } else {
      console.log('\n[2/2] Cuentas sintéticas: desactivadas (VECINOS_SINTETICOS=0).');
    }

    console.log(`\nDatos listos: ${await contar(db, 'sectores')} sectores, ${await contar(db, 'usuarios')} cuentas `
      + `(${await contar(db, 'usuarios', { datosDeDemostracion: true })} sintéticas generadas por el sistema).`);
  } finally {
    await cliente.close();
  }
}

const [comando = 'inicial', ...resto] = process.argv.slice(2);
const alias = { verificar: 'verificar-datos.mjs', totp: 'codigo-totp.mjs', monitor: 'carga/monitor-bd.mjs' };

try {
  if (comando === 'inicial') {
    await inicial();
  } else {
    const script = alias[comando] ?? (comando.endsWith('.mjs') ? comando : `${comando}.mjs`);
    await correr(script, resto);
  }
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
