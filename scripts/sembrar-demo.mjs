#!/usr/bin/env node
// NO corre en el arranque (ADR-094): la instancia real no lleva un escenario inventado. Es una herramienta a mano para dejar un
// mapa con barrios afectados **por el camino real**: envía reportes ciudadanos a la API (`POST /api/reportes`), cada uno con su
// propio dispositivo, hasta que el consenso del backend cambia el estado del barrio. Sirve para las pruebas E2E del frontend.
// El evento de la bitácora lo escribe el backend, con sus reportes de sustento (RF011): este script no toca Mongo, no borra la
// bitácora (RF028) ni marca barrios «con servicio» sin dato (ADR-014).
//
// Uso (con el backend levantado y los sectores sembrados):
//   node scripts/sembrar-demo.mjs
//   node scripts/sembrar-demo.mjs --sin-agua 3 --presion-baja 2
//
// Variables: API_URL (por defecto http://localhost:8081).
//
// Desde F2 cada reporte necesita su identidad: se pide un token de dispositivo por reporte (POST /api/dispositivos, 10 por hora por
// IP: para más, levanta el backend con RATE_LIMIT_FACTOR=100) y se envía una coordenada dentro del barrio con precisión de 10 m, que
// es lo que el quórum exige para contar un voto como verificado. Con más de una red distinta por votante (el valor por defecto del
// backend, 2) un solo equipo no alcanza el quórum: el compose local arranca con AGUAVIGIA_CONSENSO_REDES_MINIMAS=1.
//
// Elige los barrios de menor población (umbral de consenso más bajo) que aún no están afectados, y deja fuera
// los que usan las pruebas E2E. El límite por IP de /api/reportes (30 por minuto) se respeta: ante un 429 espera
// lo que indique Retry-After.

import { parseArgs } from 'node:util';
import { conDispositivo, crearDispositivo, puntosPorBarrio } from './lib/identidad-api.mjs';

const { values } = parseArgs({
  options: {
    'sin-agua': { type: 'string', default: '2' },
    'presion-baja': { type: 'string', default: '2' },
  },
});
const API = (process.env.API_URL ?? 'http://localhost:8081').replace(/\/$/, '');
const RESERVADOS = new Set(['zona-industrial', 'alameda-la-victoria', 'arroyo-grande', 'manga']);
const ESTADO_POR_TIPO = { SIN_AGUA: 'SIN_SERVICIO', PRESION_BAJA: 'PRESION_BAJA' };
// Espejo de la estrategia proporcional por defecto (ConsensoConfig): clamp(ceil(poblacion * 0,001), 3, 15) (D17).
const umbral = (poblacion) => Math.min(15, Math.max(3, Math.ceil((poblacion ?? 0) * 0.001)));
const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

async function reportar(sectorId, tipo, coordenada) {
  const dispositivo = await crearDispositivo(API);
  for (;;) {
    const respuesta = await fetch(`${API}/api/reportes`, {
      method: 'POST',
      headers: conDispositivo(dispositivo),
      body: JSON.stringify({ sectorId, tipo, coordenada, precisionMetros: 10 }),
    });
    if (respuesta.status === 429) {
      const segundos = Number(respuesta.headers.get('retry-after') ?? 5);
      process.stdout.write(`  límite por IP: espero ${segundos} s\n`);
      await esperar(segundos * 1000);
      continue;
    }
    if (respuesta.status !== 201) {
      throw new Error(`POST /api/reportes (${sectorId}, ${tipo}) respondió ${respuesta.status}: ${await respuesta.text()}`);
    }
    return;
  }
}

async function estadoDe(sectorId) {
  const respuesta = await fetch(`${API}/api/sectores/${sectorId}`);
  if (!respuesta.ok) throw new Error(`GET /api/sectores/${sectorId} respondió ${respuesta.status}`);
  return (await respuesta.json()).estado;
}

async function esperarEstado(sectorId, esperado) {
  // El consenso se evalúa como mucho una vez por segundo y sector, más el barrido (ADR-053).
  for (let intento = 0; intento < 20; intento++) {
    if (await estadoDe(sectorId) === esperado) return true;
    await esperar(500);
  }
  return false;
}

// El backend sirve los sectores desde una caché de Redis de hasta 15 s, y sembrar-sectores.mjs escribe directo en
// Mongo: justo después de sembrar, la lista puede llegar vacía o vieja. Se espera a que expire antes de rendirse.
async function leerSectores() {
  for (let intento = 0; ; intento++) {
    const respuesta = await fetch(`${API}/api/sectores`);
    if (!respuesta.ok) throw new Error(`GET /api/sectores respondió ${respuesta.status}: ¿está levantado el backend en ${API}?`);
    const { sectores } = await respuesta.json();
    if (sectores.length > 0) return sectores;
    if (intento >= 8) throw new Error('No hay sectores: corre antes node scripts/sembrar-sectores.mjs');
    process.stdout.write('  sin sectores todavía (caché del backend): espero 3 s\n');
    await esperar(3000);
  }
}

async function main() {
  const sectores = await leerSectores();
  const puntos = await puntosPorBarrio(API);

  const candidatos = sectores
    .filter((s) => !RESERVADOS.has(s.id) && s.estado !== 'SIN_SERVICIO' && s.estado !== 'PRESION_BAJA' && s.poblacion)
    .sort((a, b) => a.poblacion - b.poblacion);
  const plan = [
    ...Array(Number(values['sin-agua'])).fill('SIN_AGUA'),
    ...Array(Number(values['presion-baja'])).fill('PRESION_BAJA'),
  ].map((tipo, i) => ({ tipo, sector: candidatos[i] })).filter((p) => p.sector);

  let fallidos = 0;
  for (const { tipo, sector } of plan) {
    const necesarios = umbral(sector.poblacion);
    process.stdout.write(`${sector.nombre} (${sector.id}): ${necesarios} reportes ${tipo}\n`);
    for (let i = 0; i < necesarios; i++) await reportar(sector.id, tipo, puntos.get(sector.id));
    if (await esperarEstado(sector.id, ESTADO_POR_TIPO[tipo])) {
      process.stdout.write(`  ✓ el consenso lo pasó a ${ESTADO_POR_TIPO[tipo]}\n`);
    } else {
      fallidos++;
      process.stdout.write(`  ✗ el estado no cambió (¿otra estrategia o umbral de consenso, o redes-minimas > 1?)\n`);
    }
  }
  process.stdout.write(`Listo: ${plan.length - fallidos} de ${plan.length} barrios cambiaron por consenso real.\n`);
  if (fallidos > 0) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
