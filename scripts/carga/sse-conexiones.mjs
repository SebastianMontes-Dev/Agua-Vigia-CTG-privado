#!/usr/bin/env node
/**
 * Escala — conexiones simultáneas al SSE (ADR-049). k6 no trae soporte SSE sin una extensión, así que
 * esto es un cliente Node mínimo: abre N conexiones a GET /api/sectores/stream, las mantiene y mide.
 *
 * Uso:
 *   node scripts/carga/sse-conexiones.mjs --conexiones 2000 --rampa 200 --duracion 60
 *   node scripts/carga/sse-conexiones.mjs --url http://localhost:8081/api/sectores/stream --conexiones 20000
 *
 * Opciones (todas con valor por defecto):
 *   --url          endpoint SSE            (http://localhost:8081/api/sectores/stream)
 *   --conexiones   cuántas abrir           (1000)
 *   --rampa        conexiones nuevas por s (200)
 *   --duracion     segundos a mantenerlas  (60)
 *
 * Qué informa: conexiones abiertas y rechazadas (429 = tope del backend, esperado al superarlo),
 * latencia hasta el primer evento (p50/p95/max), eventos y latidos recibidos, y memoria del propio
 * cliente. Para ver el costo en el servidor, mira `docker stats` y `/actuator/metrics` mientras corre.
 *
 * LÍMITES DE LA PRUEBA LOCAL: cada conexión gasta un puerto efímero del cliente (~28 000 en Windows,
 * ~28 000 en Linux por defecto) y un descriptor de archivo. Superar unos 20 000 desde una sola máquina
 * exige subir esos límites o repartir el cliente entre varias; el resultado se reporta como "hasta
 * donde llegó el cliente", no como el techo del servidor. `nginx` además limita a 20 conexiones SSE
 * por IP (`limit_conn`), así que contra el proxy un solo cliente verá 429 a partir de la 21.
 */
import http from 'node:http';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
    options: {
        url: { type: 'string', default: 'http://localhost:8081/api/sectores/stream' },
        conexiones: { type: 'string', default: '1000' },
        rampa: { type: 'string', default: '200' },
        duracion: { type: 'string', default: '60' },
    },
});
const objetivo = Number(values.conexiones);
const rampa = Number(values.rampa);
const duracionMs = Number(values.duracion) * 1000;
const url = new URL(values.url);

const agente = new http.Agent({ keepAlive: true, maxSockets: Infinity });
const primerEventoMs = [];
const estados = new Map();
let abiertas = 0;
let eventos = 0;
let latidos = 0;

function abrir() {
    const inicio = performance.now();
    let sinPrimerEvento = true;
    const peticion = http.request(url, { agent: agente, headers: { Accept: 'text/event-stream' } }, (respuesta) => {
        estados.set(respuesta.statusCode, (estados.get(respuesta.statusCode) ?? 0) + 1);
        if (respuesta.statusCode !== 200) {
            respuesta.resume();
            return;
        }
        abiertas += 1;
        respuesta.setEncoding('utf8');
        respuesta.on('data', (trozo) => {
            if (sinPrimerEvento && trozo.includes('event:')) {
                sinPrimerEvento = false;
                primerEventoMs.push(performance.now() - inicio);
            }
            eventos += (trozo.match(/event:/g) ?? []).length;
            latidos += (trozo.match(/:latido/g) ?? []).length;
        });
        respuesta.on('close', () => { abiertas -= 1; });
    });
    peticion.on('error', () => estados.set('error', (estados.get('error') ?? 0) + 1));
    peticion.end();
}

function percentil(valores, p) {
    if (valores.length === 0) return NaN;
    const orden = [...valores].sort((a, b) => a - b);
    return orden[Math.min(orden.length - 1, Math.floor((p / 100) * orden.length))];
}

let lanzadas = 0;
const rampaTimer = setInterval(() => {
    for (let i = 0; i < rampa && lanzadas < objetivo; i += 1) {
        abrir();
        lanzadas += 1;
    }
    if (lanzadas >= objetivo) clearInterval(rampaTimer);
}, 1000);

const progreso = setInterval(() => {
    const mb = (process.memoryUsage().rss / 1048576).toFixed(0);
    console.log(`lanzadas=${lanzadas} abiertas=${abiertas} eventos=${eventos} latidos=${latidos} rss_cliente=${mb}MB`);
}, 5000);

setTimeout(() => {
    clearInterval(rampaTimer);
    clearInterval(progreso);
    console.log('\n=== Resultado ===');
    console.log(`objetivo=${objetivo} lanzadas=${lanzadas} abiertas_al_cierre=${abiertas}`);
    console.log('respuestas por estado:', Object.fromEntries(estados));
    console.log(`primer evento: p50=${percentil(primerEventoMs, 50).toFixed(0)}ms `
        + `p95=${percentil(primerEventoMs, 95).toFixed(0)}ms max=${Math.max(...primerEventoMs, 0).toFixed(0)}ms`);
    console.log(`eventos recibidos=${eventos} latidos recibidos=${latidos}`);
    agente.destroy();
    process.exit(0);
}, duracionMs);
