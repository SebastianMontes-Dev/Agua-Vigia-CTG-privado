#!/usr/bin/env node
// Punto de entrada del servicio `sembrador` de docker-compose.yml (ADR-086): deja la base lista para presentar con un solo
// `docker compose up`, sin instalar Node en el equipo. Orquesta los scripts de siembra que ya existían; no los reescribe.
//
//   docker compose up                                   → corre `inicial` y termina
//   docker compose run --rm sembrador verificar         → conteo de cada colección contra los mínimos
//   docker compose run --rm sembrador totp <SECRETO>    → código de 6 dígitos del segundo factor (codigo-totp.mjs)
//   docker compose run --rm sembrador <script> [args]   → cualquier otro script de scripts/, p. ej. agregar-usuarios
//
// Cada paso de `inicial` tiene su propia puerta, así que repetir `docker compose up` no duplica nada:
//   sectores       solo si hay menos de 211 (sembrar-sectores.mjs borra y vuelve a insertar)
//   30 000 cuentas solo si hay menos de 30 000 de demostración
//   mapa con vida  solo si no hay reportes fuera del histórico (reportes reales por la API hasta que el consenso cambie barrios)
//   histórico      solo si no hay cortes entre mayo y julio de 2026, el rango que escribe sembrar-historico-cortes.mjs

import { spawn } from 'node:child_process';
import { MongoClient } from 'mongodb';

const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';
const SECTORES_ESPERADOS = 211;
const MINIMO_USUARIOS = Number(process.env.MINIMO_USUARIOS ?? 30000);
// Rango de sembrar-historico-cortes.mjs: lo que cae dentro es sintético; lo de fuera lo produjo la aplicación.
const HISTORICO = { $gte: new Date('2026-05-01T00:00:00Z'), $lte: new Date('2026-07-31T23:59:59Z') };
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

async function conteos(db) {
  const n = (coleccion, filtro = {}) => db.collection(coleccion).countDocuments(filtro);
  return {
    sectores: await n('sectores'),
    usuarios: await n('usuarios'),
    usuariosDemo: await n('usuarios', { datosDeDemostracion: true }),
    admins: await n('usuarios', { rol: 'ADMIN' }),
    cortes: await n('cortes'),
    reportes: await n('reportes'),
    cortesHistoricos: await n('cortes', { inicio: HISTORICO }),
    reportesVivos: await n('reportes', { timestamp: { $not: HISTORICO } }),
  };
}

// El backend crea al primer ADMIN al terminar de arrancar, y solo si no existe ninguna cuenta: las 30 000 van después.
async function esperarAdmin(db) {
  for (let intento = 0; intento < 20; intento++) {
    if (await db.collection('usuarios').countDocuments({ rol: 'ADMIN' }) > 0) return true;
    await esperar(3000);
  }
  return false;
}

async function inicial() {
  const cliente = await conectar();
  try {
    const db = cliente.db(DB_NAME);
    let c = await conteos(db);

    if (c.sectores < SECTORES_ESPERADOS) {
      console.log(`\n[1/4] Sectores: hay ${c.sectores}, se siembran los ${SECTORES_ESPERADOS} barrios de Cartagena.`);
      await correr('sembrar-sectores.mjs');
    } else {
      console.log(`\n[1/4] Sectores: ya están los ${c.sectores}.`);
    }

    c = await conteos(db);
    if (c.usuariosDemo < MINIMO_USUARIOS) {
      if (c.admins === 0 && !(await esperarAdmin(db))) {
        console.warn('AVISO: el backend no creó al ADMIN inicial (¿ADMIN_INICIAL_CORREO vacío en .env?). Sigo sin él.');
      }
      console.log(`\n[2/4] Cuentas: hay ${c.usuariosDemo} de demostración, se siembran ${MINIMO_USUARIOS}.`);
      await correr('sembrar-usuarios-demo.mjs', ['--cantidad', String(MINIMO_USUARIOS), '--minimo', String(MINIMO_USUARIOS)]);
    } else {
      console.log(`\n[2/4] Cuentas: ya hay ${c.usuariosDemo} de demostración.`);
    }

    c = await conteos(db);
    if (c.reportesVivos === 0) {
      console.log('\n[3/4] Mapa: se envían reportes reales por la API hasta que el consenso cambie algunos barrios.');
      try {
        await correr('sembrar-demo.mjs');
      } catch (error) {
        console.warn(`AVISO: el mapa quedó sin barrios afectados (${error.message}). El resto de los datos está listo.`);
      }
    } else {
      console.log(`\n[3/4] Mapa: ya hay ${c.reportesVivos} reportes recientes.`);
    }

    c = await conteos(db);
    if (c.cortesHistoricos === 0) {
      console.log('\n[4/4] Histórico: se siembran cortes y reportes de mayo–julio de 2026 (datos sintéticos).');
      await correr('sembrar-historico-cortes.mjs');
    } else {
      console.log(`\n[4/4] Histórico: ya hay ${c.cortesHistoricos} cortes de mayo–julio.`);
    }

    c = await conteos(db);
    console.log(`\nDatos listos: ${c.sectores} sectores, ${c.usuarios} usuarios, ${c.cortes} cortes, ${c.reportes} reportes.`);
    if (c.usuarios < MINIMO_USUARIOS) {
      console.error(`FALLA: 'usuarios' tiene ${c.usuarios} documentos y se exigen al menos ${MINIMO_USUARIOS}.`);
      process.exitCode = 1;
    }
  } finally {
    await cliente.close();
  }
}

const [comando = 'inicial', ...resto] = process.argv.slice(2);
const alias = { verificar: 'verificar-datos.mjs', totp: 'codigo-totp.mjs' };

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
