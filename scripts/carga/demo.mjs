#!/usr/bin/env node
/**
 * Demo de carga para la presentación (ADR-083): una ciudad entera reportando a la vez, contra el backend real, con el
 * mapa cambiando en vivo. Deja el sistema como estaba al terminar.
 *
 *   node scripts/carga/demo.mjs --usuarios 30000 --ventana 60 --conectados 30000
 *
 * Qué hace, en orden:
 *   1. Comprueba el stack (Mongo, Redis y backend) y que haya al menos 30 000 cuentas y los sectores (si faltan las
 *      cuentas, las siembra con scripts/sembrar-usuarios-demo.mjs).
 *   2. Hace un respaldo de Mongo: las escrituras dejan decenas de miles de reportes y los estados de los barrios
 *      cambian. Es la única forma honesta de volver atrás (borrar reportes sueltos dejaría eventos «confirmado por
 *      ciudadanos» sin sustento, justo el BUG-122).
 *   3. Reinicia el backend con el perfil `carga` (sin límite de peticiones por IP y con más margen de conexiones).
 *   4. Abre las conexiones SSE (el mapa en vivo de CONECTADOS vecinos), repartidas en contenedores porque cada uno
 *      agota sus puertos efímeros hacia el backend cerca de los 28 000.
 *   5. Lanza k6 dentro de la red de Docker con su panel en vivo en http://localhost:5665 y el informe HTML en
 *      resultados/<fecha>/informe.html.
 *   6. Imprime el resumen final y devuelve el backend a su perfil normal.
 *
 * Para verlo en vivo, antes de empezar: el mapa con `cd frontend && npm run dev` (http://localhost:5173) y el panel de
 * k6 en http://localhost:5665 (aparece al arrancar la prueba). Con --esperar el script se detiene antes de disparar
 * para que abras ambos.
 *
 * Opciones (todas con valor por defecto):
 *   --usuarios 30000       reportes en total, uno por vecino
 *   --ventana 60           segundos en que llegan
 *   --conectados 30000     conexiones SSE simultáneas (0 = sin SSE)
 *   --focos 12             barrios con avería masiva
 *   --lectores 150         lecturas del mapa por segundo
 *   --veedores 2           inicios de sesión de veedores por segundo
 *   --suscripciones 1      altas de suscripción por segundo
 *   --registros 0          cuentas nuevas por la API real (POST /api/cuentas/registro), en paralelo con los reportes
 *   --tasa-registros 50    registros por segundo. Con los reportes a la vez, 50/s cumple todos los umbrales; solos, el techo
 *                          medido es ≈ 160/s en un PC de 12 hilos (lo pone el BCrypt de cada alta)
 *   --sin-correo           los registros no envían el correo de verificación (no llenan MailHog; ADR-088)
 *   --rampa-sse 1500       conexiones SSE nuevas por segundo, por contenedor
 *   --esperar              esperar Enter antes de disparar la carga
 *   --restaurar            al terminar, volver Mongo y Redis al estado de antes (usa el respaldo del paso 2)
 *   --sin-respaldo         no hacer el respaldo de Mongo
 *   --sin-reinicio         no tocar el backend (debe estar ya con el perfil `carga`)
 *   --dejar-perfil         no devolver el backend a su perfil normal al terminar
 *   --sobre-datos-reales   seguir aunque la base tenga reportes, cortes, propuestas o cuentas reales (por defecto se niega)
 *   --proyecto agua-vigia-ctg   nombre del proyecto de Docker Compose
 *
 * LÍMITE FÍSICO: el generador y el backend comparten el mismo PC. Las cifras dicen el orden de magnitud que aguanta
 * esta máquina, no la capacidad de un servidor (ver docs/ingenieria/escalabilidad.md).
 */
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline/promises';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { MongoClient } from 'mongodb';
import { buscarDatosReales, mensajeDatosReales } from '../lib/datos-reales.mjs';

const { values } = parseArgs({
    options: {
        usuarios: { type: 'string', default: '30000' },
        ventana: { type: 'string', default: '60' },
        conectados: { type: 'string', default: '30000' },
        focos: { type: 'string', default: '12' },
        lectores: { type: 'string', default: '150' },
        veedores: { type: 'string', default: '2' },
        suscripciones: { type: 'string', default: '1' },
        registros: { type: 'string', default: '0' },
        'tasa-registros': { type: 'string', default: '50' },
        'sin-correo': { type: 'boolean', default: false },
        'rampa-sse': { type: 'string', default: '1500' },
        esperar: { type: 'boolean', default: false },
        'sin-respaldo': { type: 'boolean', default: false },
        restaurar: { type: 'boolean', default: false },
        'sin-reinicio': { type: 'boolean', default: false },
        'dejar-perfil': { type: 'boolean', default: false },
        'sobre-datos-reales': { type: 'boolean', default: false },
        proyecto: { type: 'string', default: process.env.COMPOSE_PROJECT_NAME ?? 'agua-vigia-ctg' },
    },
});

const USUARIOS = Number(values.usuarios);
const VENTANA = Number(values.ventana);
const CONECTADOS = Number(values.conectados);
const FOCOS = Number(values.focos);
const RAMPA_SSE = Number(values['rampa-sse']);
const REGISTROS = Number(values.registros);
const TASA_REGISTROS = Number(values['tasa-registros']);
// Los registros llevan su propia duración (su techo es ≈ 160/s): la carga dura lo que tarde la más larga de las dos.
const SEGUNDOS_REGISTROS = REGISTROS > 0 ? Math.ceil(REGISTROS / TASA_REGISTROS) : 0;
const DURACION = Math.max(Number(values.ventana), SEGUNDOS_REGISTROS);
const PROYECTO = values.proyecto;
const RED = `${PROYECTO}_aguavigia`;
const MINIMO_CUENTAS = 30000;
const MAX_SSE_POR_CONTENEDOR = 20000;

for (const [nombre, valor, minimo] of [['usuarios', USUARIOS, 1], ['ventana', VENTANA, 5], ['conectados', CONECTADOS, 0],
    ['focos', FOCOS, 0], ['rampa-sse', RAMPA_SSE, 1], ['registros', REGISTROS, 0], ['tasa-registros', TASA_REGISTROS, 1]]) {
    if (!Number.isInteger(valor) || valor < minimo) {
        console.error(`--${nombre} debe ser un entero mayor o igual que ${minimo}`);
        process.exit(1);
    }
}

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const DIR_CARGA = path.join(RAIZ, 'scripts', 'carga');
const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const API_LOCAL = 'http://localhost:8081';
const ENV_DOCKER = { ...process.env, MSYS_NO_PATHCONV: '1' };

const ahora = new Date();
const sello = `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(ahora.getDate()).padStart(2, '0')}`
    + `T${String(ahora.getHours()).padStart(2, '0')}-${String(ahora.getMinutes()).padStart(2, '0')}-${String(ahora.getSeconds()).padStart(2, '0')}`;
const DIR_RESULTADOS = path.join(RAIZ, 'resultados', sello);

const paso = (texto) => console.log(`\n▶ ${texto}`);
const pausa = (ms) => new Promise((resolver) => setTimeout(resolver, ms));
const formato = (n) => (typeof n === 'number' && Number.isFinite(n) ? n.toLocaleString('es-CO', { maximumFractionDigits: 0 }) : String(n ?? '—'));
const ms = (n) => (typeof n === 'number' && Number.isFinite(n) ? `${n.toLocaleString('es-CO', { maximumFractionDigits: 0 })} ms` : '—');

function docker(argumentos, opciones = {}) {
    return spawnSync('docker', argumentos, { encoding: 'utf8', env: ENV_DOCKER, cwd: RAIZ, ...opciones });
}

function compose(conCarga, argumentos, opciones = {}) {
    const archivos = ['-f', 'docker-compose.yml', ...(conCarga ? ['-f', 'docker-compose.carga.yml'] : [])];
    return docker(['compose', '-p', PROYECTO, ...archivos, ...argumentos], opciones);
}

function fallar(mensaje) {
    console.error(`\n✗ ${mensaje}`);
    process.exit(1);
}

function comprobarStack() {
    for (const contenedor of ['aguavigia-mongo', 'aguavigia-redis', 'aguavigia-backend']) {
        const r = docker(['inspect', '-f', '{{.State.Status}}', contenedor]);
        if (r.status !== 0 || r.stdout.trim() !== 'running') {
            fallar(`El contenedor ${contenedor} no está corriendo. Levanta el stack con: docker compose up -d --build --wait`);
        }
    }
    const red = docker(['network', 'inspect', RED, '-f', '{{.Name}}']);
    if (red.status !== 0) fallar(`No existe la red ${RED}. Revisa el nombre del proyecto con --proyecto (docker network ls).`);
}

async function esperarBackend(segundos = 180) {
    const limite = Date.now() + segundos * 1000;
    while (Date.now() < limite) {
        try {
            const r = await fetch(`${API_LOCAL}/actuator/health/readiness`);
            if (r.ok) {
                const s = await fetch(`${API_LOCAL}/api/sectores`);
                if (s.ok) return;
            }
        } catch { /* todavía arrancando */ }
        await pausa(2000);
    }
    fallar(`El backend no quedó sano en ${segundos} s. Mira: docker logs aguavigia-backend`);
}

/** Un perfil que no existe se ignora en silencio: si el límite por IP siguiera activo, la prueba mediría el 429 y no el backend. */
async function comprobarSinLimitePorIp() {
    for (let i = 0; i < 40; i++) {
        const r = await fetch(`${API_LOCAL}/api/reportes`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
        if (r.status === 429) {
            fallar('El backend sigue con el límite de peticiones por IP (429): no tiene el perfil `carga`. Reconstruye la imagen con --build.');
        }
    }
}

async function foto(db) {
    const sectores = await db.collection('sectores').find({}, { projection: { slug: 1, nombre: 1, estadoActual: 1 } }).toArray();
    const porTipo = await db.collection('eventos_bitacora').aggregate([{ $group: { _id: '$tipo', n: { $sum: 1 } } }]).toArray();
    return {
        reportes: await db.collection('reportes').countDocuments({}),
        eventos: await db.collection('eventos_bitacora').countDocuments({}),
        usuarios: await db.collection('usuarios').countDocuments({}),
        suscripciones: await db.collection('suscripciones').countDocuments({}),
        registrosDeLaCarga: await db.collection('usuarios').countDocuments({ correo: { $regex: '@carga\\.aguavigia\\.local$' } }),
        estados: Object.fromEntries(sectores.map((s) => [s.slug, s.estadoActual ?? null])),
        nombres: Object.fromEntries(sectores.map((s) => [s.slug, s.nombre])),
        eventosPorTipo: Object.fromEntries(porTipo.map((t) => [t._id, t.n])),
    };
}

/** Pico de CPU y memoria de cada contenedor mientras dura la carga (docker stats cada pocos segundos). */
function iniciarMuestreo() {
    const picos = {};
    let activo = true;
    const ciclo = async () => {
        while (activo) {
            const salida = await new Promise((resolver) => {
                let texto = '';
                const p = spawn('docker', ['stats', '--no-stream', '--format', '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}', 'aguavigia-backend', 'aguavigia-mongo', 'aguavigia-redis'], { env: ENV_DOCKER });
                p.stdout.on('data', (d) => { texto += d; });
                p.on('close', () => resolver(texto));
            });
            for (const linea of salida.split('\n').filter(Boolean)) {
                const [nombre, cpu, memoria] = linea.split('|');
                const cpuNum = parseFloat(cpu);
                // "6.4GiB / 15.17GiB": la unidad que cuenta es la de lo usado, no la del tope.
                const usada = memoria.split('/')[0];
                const memNum = parseFloat(usada) * (usada.includes('GiB') ? 1024 : 1);
                const pico = picos[nombre] ?? { cpu: 0, memMiB: 0 };
                picos[nombre] = { cpu: Math.max(pico.cpu, cpuNum || 0), memMiB: Math.max(pico.memMiB, memNum || 0) };
            }
        }
    };
    const tarea = ciclo();
    return async () => { activo = false; await tarea; return picos; };
}

function limpiarContenedores() {
    const nombres = docker(['ps', '-a', '--filter', 'name=aguavigia-carga-', '--format', '{{.Names}}']).stdout.split('\n').filter(Boolean);
    if (nombres.length > 0) docker(['rm', '-f', ...nombres]);
}

/**
 * Deja Mongo y Redis como estaban antes de la corrida. Es la única vuelta atrás honesta: borrar solo los reportes
 * dejaría en la bitácora eventos «confirmado por ciudadanos» sin nada que los sustente (BUG-122). Redis guarda el
 * cupo por dispositivo (RF006) y la ventana del consenso, que sin esto seguirían contando reportes que ya no existen.
 */
function restaurarEstadoDeAntes(archivo) {
    paso('Volviendo al estado de antes (--restaurar)');
    if (!archivo || !fs.existsSync(archivo)) fallar('No encuentro el archivo del respaldo para restaurar.');
    const entrada = fs.openSync(archivo, 'r');
    const r = compose(false, ['exec', '-T', 'mongo', 'mongorestore', '--nsInclude', `${process.env.MONGODB_DB ?? 'aguavigia'}.*`, '--archive', '--gzip', '--drop'],
        { stdio: [entrada, 'pipe', 'pipe'] });
    fs.closeSync(entrada);
    if (r.status !== 0) fallar(`No se pudo restaurar Mongo:\n${r.stderr}`);
    for (const patron of ['cupo:*', 'consenso:*']) {
        docker(['exec', 'aguavigia-redis', 'sh', '-c', `redis-cli --scan --pattern '${patron}' | xargs -r -n 500 redis-cli unlink > /dev/null`]);
    }
    console.log('  Mongo restaurado desde el respaldo y Redis sin cupos ni ventanas de consenso de la corrida.');
}

let backendConPerfilCarga = false;
async function devolverBackend() {
    if (!backendConPerfilCarga || values['dejar-perfil']) return;
    paso('Devolviendo el backend a su perfil normal');
    compose(false, ['up', '-d', '--force-recreate', 'backend'], { stdio: 'ignore' });
    await esperarBackend();
    backendConPerfilCarga = false;
    console.log('  Backend normal de nuevo (con el límite de peticiones por IP y el tope de SSE de siempre).');
}

let terminando = false;
async function terminar(codigo) {
    if (terminando) return;
    terminando = true;
    limpiarContenedores();
    await devolverBackend();
    process.exit(codigo);
}
process.on('SIGINT', () => { console.log('\nInterrumpido: limpiando…'); terminar(130); });

function leerResumenK6() {
    const archivo = path.join(DIR_RESULTADOS, 'resumen.json');
    if (!fs.existsSync(archivo)) return null;
    const metricas = JSON.parse(fs.readFileSync(archivo, 'utf8')).metrics ?? {};
    const valor = (nombre, clave) => {
        const m = metricas[nombre];
        return m ? (m.values?.[clave] ?? m[clave]) : undefined;
    };
    return { valor };
}

async function main() {
    console.log(`Demo de carga — ${formato(USUARIOS)} reportes en ${VENTANA} s, ${formato(CONECTADOS)} conexiones en vivo, ${FOCOS} focos`
        + (REGISTROS > 0 ? `, ${formato(REGISTROS)} registros de cuentas a ${TASA_REGISTROS}/s (${SEGUNDOS_REGISTROS} s)${values['sin-correo'] ? ' sin correo' : ''}.` : '.'));
    fs.mkdirSync(DIR_RESULTADOS, { recursive: true });

    paso('Comprobando el stack');
    comprobarStack();
    limpiarContenedores();
    const cliente = new MongoClient(MONGODB_URI);
    await cliente.connect();
    const db = cliente.db(process.env.MONGODB_DB ?? 'aguavigia');
    try {
        const sectores = await db.collection('sectores').countDocuments({});
        if (sectores === 0) fallar('No hay sectores en la base. Corre: node scripts/sembrar-sectores.mjs');
        if (!values['sobre-datos-reales']) {
            const reales = await buscarDatosReales(db);
            if (reales.length > 0) fallar(mensajeDatosReales(reales));
        }
        let cuentas = await db.collection('usuarios').countDocuments({});
        if (cuentas < MINIMO_CUENTAS) {
            console.log(`  Hay ${formato(cuentas)} cuentas y se exigen ${formato(MINIMO_CUENTAS)}: sembrando…`);
            const siembra = spawnSync('node', [path.join(RAIZ, 'scripts', 'sembrar-usuarios-demo.mjs')], { stdio: 'inherit', cwd: path.join(RAIZ, 'scripts') });
            if (siembra.status !== 0) fallar('No se pudieron sembrar las cuentas de demostración.');
            cuentas = await db.collection('usuarios').countDocuments({});
        }
        console.log(`  ${formato(sectores)} barrios · ${formato(cuentas)} cuentas.`);

        if (values.restaurar && values['sin-respaldo']) fallar('--restaurar necesita el respaldo de antes: quita --sin-respaldo.');
        let archivoRespaldo = null;
        if (!values['sin-respaldo']) {
            paso('Respaldo de Mongo (para poder volver al estado de antes)');
            const copia = spawnSync('bash', [path.join(RAIZ, 'scripts', 'backup-mongo.sh')], { encoding: 'utf8', cwd: RAIZ, env: { ...process.env, COMPOSE_PROJECT_NAME: PROYECTO } });
            if (copia.status !== 0) fallar(`El respaldo de Mongo falló:\n${copia.stderr}`);
            const linea = copia.stdout.trim().split('\n').pop();
            console.log(`  ${linea}`);
            archivoRespaldo = /escrito en (.+?) \(/.exec(linea)?.[1] ?? null;
            if (values.restaurar) console.log('  Al terminar se restaura este respaldo (--restaurar).');
            else console.log('  Para volver atrás después: ./scripts/restore-mongo.sh <ese archivo>');
        }

        if (!values['sin-reinicio']) {
            paso('Reiniciando el backend con el perfil de carga');
            const entorno = { ...ENV_DOCKER, CORREO_CUENTAS_HABILITADO: values['sin-correo'] ? 'false' : 'true' };
            const r = compose(true, ['up', '-d', '--build', '--force-recreate', 'backend'], { stdio: 'ignore', env: entorno });
            if (r.status !== 0) fallar('No se pudo levantar el backend con docker-compose.carga.yml.');
            backendConPerfilCarga = true;
        }
        await esperarBackend();
        await comprobarSinLimitePorIp();
        console.log('  Backend listo y sin límite de peticiones por IP.');

        const veedores = (await db.collection('usuarios')
            .find({ datosDeDemostracion: true, estado: 'ACTIVA', rol: 'VEEDOR', secretoTotp: null }, { projection: { correo: 1 } })
            .limit(300).toArray()).map((u) => u.correo);

        const antes = await foto(db);
        const disponibles = Object.values(antes.estados).filter((e) => e !== 'SIN_SERVICIO').length;
        if (FOCOS > 0 && disponibles < FOCOS * 2) {
            console.warn(`  AVISO: solo ${disponibles} barrios no están ya en SIN_SERVICIO: la avería masiva casi no cambiará el mapa. `
                + 'Vuelve al estado de antes con --restaurar (o restaurando un respaldo).');
        }

        // ── SSE: el mapa en vivo de los vecinos conectados ────────────────────────────────────────────────
        const contenedoresSse = [];
        if (CONECTADOS > 0) {
            const cuantos = Math.ceil(CONECTADOS / MAX_SSE_POR_CONTENEDOR);
            const porContenedor = Math.ceil(CONECTADOS / cuantos);
            const segundosRampa = Math.ceil(porContenedor / RAMPA_SSE);
            const duracion = segundosRampa + 10 + DURACION + 45;
            paso(`Abriendo ${formato(CONECTADOS)} conexiones en vivo (${cuantos} contenedor${cuantos > 1 ? 'es' : ''}, ${segundosRampa} s de rampa)`);
            for (let i = 1; i <= cuantos; i++) {
                const nombre = `aguavigia-carga-sse-${i}`;
                const r = docker(['run', '-d', '--name', nombre, '--network', RED, '--ulimit', 'nofile=1048576:1048576',
                    '-v', `${DIR_CARGA}:/carga:ro`, 'node:22-alpine', 'node', '/carga/sse-conexiones.mjs',
                    '--url', 'http://backend:8080/api/sectores/stream', '--conexiones', String(porContenedor),
                    '--rampa', String(RAMPA_SSE), '--duracion', String(duracion)]);
                if (r.status !== 0) fallar(`No se pudo lanzar el cliente SSE ${i}: ${r.stderr}`);
                contenedoresSse.push(nombre);
            }
            await pausa((segundosRampa + 4) * 1000);
        }

        if (values.esperar) {
            console.log('\n  Abre ahora el mapa (cd frontend && npm run dev → http://localhost:5173) y, cuando arranque la prueba,');
            console.log('  el panel de k6 en http://localhost:5665.');
            const consola = readline.createInterface({ input: process.stdin, output: process.stdout });
            await consola.question('  Pulsa Enter para disparar la carga… ');
            consola.close();
        }

        // ── k6 ────────────────────────────────────────────────────────────────────────────────────────────
        paso(`Disparando ${formato(USUARIOS)} reportes en ${VENTANA} s${REGISTROS > 0 ? ` y ${formato(REGISTROS)} registros en ${SEGUNDOS_REGISTROS} s` : ''} — panel en vivo: http://localhost:5665`);
        console.log('  Para ver crecer la base en vivo, en otra terminal: docker compose run --rm sembrador monitor');
        const inicioCarga = Date.now();
        const detenerMuestreo = iniciarMuestreo();
        const argumentosK6 = ['run', '--rm', '--name', 'aguavigia-carga-k6', '--network', RED, '-p', '5665:5665',
            '-e', 'K6_WEB_DASHBOARD=true', '-e', 'K6_WEB_DASHBOARD_HOST=0.0.0.0', '-e', 'K6_WEB_DASHBOARD_PORT=5665',
            '-e', 'K6_WEB_DASHBOARD_PERIOD=1s', '-e', 'K6_WEB_DASHBOARD_EXPORT=/resultados/informe.html',
            '-e', 'BASE_URL=http://backend:8080', '-e', `USUARIOS=${USUARIOS}`, '-e', `VENTANA=${VENTANA}`,
            '-e', `FOCOS=${FOCOS}`, '-e', `LECTORES=${values.lectores}`, '-e', `VEEDORES_TASA=${values.veedores}`,
            '-e', `SUSCRIPCIONES=${values.suscripciones}`, '-e', `VEEDORES=${veedores.join(',')}`,
            '-e', `REGISTROS=${REGISTROS}`, '-e', `TASA_REGISTROS=${TASA_REGISTROS}`,
            '-e', `CORRIDA=${sello}`,
            // Sin valor, `docker run -e VAR` hereda la variable del entorno de este proceso: la clave no queda en la línea de comandos.
            // La usan los registros (clave de las cuentas nuevas) y los inicios de sesión de veedores del generador.
            ...(process.env.CLAVE_VEEDORES ? ['-e', 'CLAVE_VEEDORES'] : []),
            '-v', `${DIR_CARGA}:/carga:ro`, '-v', `${DIR_RESULTADOS}:/resultados`,
            'grafana/k6', 'run', '--summary-export=/resultados/resumen.json', '/carga/flujo-ciudadano.js'];
        // `docker run` recibe 'run' dos veces (el subcomando y el de k6): la primera es la de docker.
        const codigoK6 = await new Promise((resolver) => {
            const proceso = spawn('docker', argumentosK6, { stdio: 'inherit', env: ENV_DOCKER, cwd: RAIZ });
            proceso.on('close', resolver);
        });
        const segundosCarga = (Date.now() - inicioCarga) / 1000;
        const picos = await detenerMuestreo();

        // ── Recoger el SSE ────────────────────────────────────────────────────────────────────────────────
        const sse = { objetivo: 0, lanzadas: 0, pico: 0, abiertasAlCierre: 0, eventos: 0, latidos: 0, estados: {}, p50: [], p95: [], max: [] };
        for (const nombre of contenedoresSse) {
            docker(['wait', nombre]);
            const salida = docker(['logs', nombre]).stdout;
            const linea = salida.split('\n').find((l) => l.startsWith('RESUMEN_JSON '));
            if (!linea) { console.warn(`  (sin resumen del cliente SSE ${nombre})`); continue; }
            const r = JSON.parse(linea.slice('RESUMEN_JSON '.length));
            sse.objetivo += r.objetivo; sse.lanzadas += r.lanzadas; sse.pico += r.pico;
            sse.abiertasAlCierre += r.abiertasAlCierre; sse.eventos += r.eventos; sse.latidos += r.latidos;
            for (const [k, v] of Object.entries(r.estados)) sse.estados[k] = (sse.estados[k] ?? 0) + v;
            sse.p50.push(r.primerEventoMs.p50); sse.p95.push(r.primerEventoMs.p95); sse.max.push(r.primerEventoMs.max);
        }
        limpiarContenedores();

        await pausa(2000);
        const despues = await foto(db);

        // ── Resumen ───────────────────────────────────────────────────────────────────────────────────────
        const k6 = leerResumenK6();
        const lineas = [];
        const dice = (texto = '') => lineas.push(texto);
        dice('═══════════════════════════════════════════════════════════════════');
        dice(` RESUMEN DE LA DEMO DE CARGA — ${sello}`);
        dice('═══════════════════════════════════════════════════════════════════');
        dice(` Escala pedida: ${formato(USUARIOS)} reportes en ${VENTANA} s · ${formato(CONECTADOS)} conexiones en vivo · ${FOCOS} focos`
            + (REGISTROS > 0 ? ` · ${formato(REGISTROS)} registros a ${TASA_REGISTROS}/s` : ''));
        dice(` Duración real de la carga: ${segundosCarga.toFixed(0)} s · k6 ${codigoK6 === 0 ? 'con todos los umbrales cumplidos' : `terminó con código ${codigoK6} (algún umbral NO se cumplió)`}`);
        dice();
        if (k6) {
            const aceptados = k6.valor('reportes_aceptados', 'count');
            const limitados = k6.valor('reportes_limitados', 'count');
            dice(' REPORTES');
            dice(`   aceptados (201):           ${formato(aceptados ?? 0)}`);
            dice(`   limitados por cupo (429):  ${formato(limitados ?? 0)}`);
            dice(`   con error (ni 201 ni 429): ${formato(k6.valor('reportes_fallidos', 'count') ?? 0)}`);
            dice(`   en barrios con avería:     ${formato(k6.valor('reportes_en_focos', 'count') ?? 0)}`);
            dice(`   confirmaciones aceptadas:  ${formato(k6.valor('confirmaciones_aceptadas', 'count') ?? 0)}`);
            dice(`   descartados por el generador: ${formato(k6.valor('dropped_iterations', 'count') ?? 0)}`);
            dice();
            dice(' LATENCIA DE UN REPORTE (RNF002: p95 < 1 000 ms)');
            const clave = 'http_req_duration{grupo:reporte}';
            dice(`   mediana ${ms(k6.valor(clave, 'med'))} · p90 ${ms(k6.valor(clave, 'p(90)'))} · p95 ${ms(k6.valor(clave, 'p(95)'))} · p99 ${ms(k6.valor(clave, 'p(99)'))} · máx ${ms(k6.valor(clave, 'max'))}`);
            const fallos = k6.valor('http_req_failed', 'rate') ?? k6.valor('http_req_failed', 'value');
            dice(`   peticiones: ${formato(k6.valor('http_reqs', 'count'))} (${formato(k6.valor('http_reqs', 'rate'))}/s) · con error: ${typeof fallos === 'number' ? (fallos * 100).toFixed(2) : '—'} %`);
            dice(`   sesiones de veedor: ${formato(k6.valor('sesiones_veedor', 'count') ?? 0)} · suscripciones creadas: ${formato(k6.valor('suscripciones_creadas', 'count') ?? 0)}`);
            dice();
            if (REGISTROS > 0) {
                const claveRegistro = 'http_req_duration{grupo:registro}';
                dice(` REGISTRO DE CUENTAS (POST /api/cuentas/registro${values['sin-correo'] ? ', sin correo' : ', con correo a MailHog'})`);
                dice(`   aceptados (202): ${formato(k6.valor('registros_aceptados', 'count') ?? 0)} de ${formato(REGISTROS)} · con error: ${formato(k6.valor('registros_fallidos', 'count') ?? 0)}`);
                dice(`   latencia: mediana ${ms(k6.valor(claveRegistro, 'med'))} · p95 ${ms(k6.valor(claveRegistro, 'p(95)'))} · máx ${ms(k6.valor(claveRegistro, 'max'))} (cada alta dura ≥ 100 ms a propósito, RNF024)`);
                dice(`   en Mongo: ${formato(despues.registrosDeLaCarga)} cuentas con correo @carga.aguavigia.local, en PENDIENTE_VERIFICACION`);
                dice();
            }
        } else {
            dice(' (k6 no dejó resumen.json: mira la salida de arriba)');
            dice();
        }
        if (CONECTADOS > 0) {
            dice(' MAPA EN VIVO (SSE)');
            dice(`   conexiones pedidas ${formato(sse.objetivo)} · pico abiertas ${formato(sse.pico)} · abiertas al cierre ${formato(sse.abiertasAlCierre)}`);
            dice(`   respuestas: ${JSON.stringify(sse.estados)}`);
            dice(`   primer evento: p50 ${ms(Math.max(...sse.p50))} · p95 ${ms(Math.max(...sse.p95))} · máx ${ms(Math.max(...sse.max))}`);
            dice(`   avisos de cambio recibidos: ${formato(sse.eventos)} (latidos ${formato(sse.latidos)})`);
            dice();
        }
        dice(' RECURSOS DEL PC (pico durante la carga; la CPU es de un núcleo: 100 % = uno, el PC tiene 12)');
        for (const [nombre, pico] of Object.entries(picos)) {
            dice(`   ${nombre.replace('aguavigia-', '').padEnd(8)} CPU ${pico.cpu.toFixed(0)} % · memoria ${formato(pico.memMiB)} MiB`);
        }
        dice('   (el generador de carga corre en este mismo PC y compite por la CPU con el backend)');
        dice();
        const cambiados = Object.keys(despues.estados).filter((s) => despues.estados[s] !== antes.estados[s]);
        dice(' EL SISTEMA (Mongo, antes → después)');
        dice(`   reportes:         ${formato(antes.reportes)} → ${formato(despues.reportes)}  (+${formato(despues.reportes - antes.reportes)})`);
        dice(`   bitácora:         ${formato(antes.eventos)} → ${formato(despues.eventos)}  (+${formato(despues.eventos - antes.eventos)} eventos de consenso y de estado)`);
        dice(`   suscripciones:    ${formato(antes.suscripciones)} → ${formato(despues.suscripciones)}`);
        dice(`   cuentas:          ${formato(antes.usuarios)} → ${formato(despues.usuarios)}`);
        dice(`   barrios que cambiaron de estado: ${cambiados.length}`);
        for (const slug of cambiados.slice(0, 15)) {
            dice(`     · ${despues.nombres[slug]}: ${antes.estados[slug] ?? 'sin datos'} → ${despues.estados[slug] ?? 'sin datos'}`);
        }
        if (cambiados.length > 15) dice(`     … y ${cambiados.length - 15} más`);
        dice();
        dice(` Informe HTML de k6: ${path.join(DIR_RESULTADOS, 'informe.html')}`);
        dice('═══════════════════════════════════════════════════════════════════');
        const texto = lineas.join('\n');
        console.log(`\n${texto}`);
        fs.writeFileSync(path.join(DIR_RESULTADOS, 'resumen.txt'), `${texto}\n`);

        await cliente.close();
        if (values.restaurar) restaurarEstadoDeAntes(archivoRespaldo);
        await terminar(codigoK6 === 0 ? 0 : 1);
    } catch (error) {
        await cliente.close().catch(() => {});
        throw error;
    }
}

main().catch(async (error) => {
    console.error(error);
    await terminar(1);
});
