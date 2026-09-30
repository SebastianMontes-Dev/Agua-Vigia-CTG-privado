/**
 * Demo de carga (ADR-083): el flujo completo de una ciudad reportando a la vez, no un solo endpoint aislado.
 *
 * Mezcla, en paralelo y durante VENTANA segundos:
 *   - reportes:        USUARIOS vecinos, cada uno con su huella, reportan una vez. Una parte lleva coordenada dentro
 *                      de su barrio (RF007) y una parte se confirma después con otra huella (RF038).
 *   - focos:           FOCOS barrios sufren una avería masiva y se «encienden» uno tras otro a lo largo de la
 *                      ventana, para que el consenso real cambie sus estados mientras el mapa se mira.
 *   - lectores:        gente mirando el mapa (lista de barrios, ficha de un barrio, bitácora).
 *   - veedores:        inicios de sesión de cuentas VEEDOR sembradas y una consulta de su panel.
 *   - suscripciones:   altas de alertas por correo, a tasa baja (cada una manda un correo a MailHog).
 *   - registros:       REGISTROS personas piden una cuenta del panel por la API real (POST /api/cuentas/registro), a
 *                      TASA_REGISTROS por segundo: el backend valida, cifra la clave con BCrypt, audita y envía el correo
 *                      de verificación. Cada alta aparece en `usuarios` en cuanto responde 202 (ADR-088).
 *
 * Todo se controla por variables de entorno:
 *   BASE_URL            http://localhost:8081   (dentro de la red de Docker: http://backend:8080)
 *   USUARIOS            30000    reportes en total (uno por vecino)
 *   VENTANA             60       segundos en que llegan
 *   FOCOS               12       barrios con avería masiva (0 = todo disperso)
 *   PORCENTAJE_FOCOS    0.85     fracción de los reportes que va a los focos
 *   CONFIRMAR           0.15     fracción de reportes que otro vecino confirma
 *   CON_COORDENADA      0.6      fracción de reportes que viaja con coordenada
 *   LECTORES            150      lecturas por segundo
 *   VEEDORES_TASA       2        inicios de sesión de veedores por segundo (0 = ninguno)
 *   VEEDORES            correos separados por coma de cuentas VEEDOR sin segundo factor
 *   CLAVE_VEEDORES      la clave de demostración que imprimió el sembrador (obligatoria si VEEDORES_TASA > 0)
 *   SUSCRIPCIONES       1        altas de suscripción por segundo (0 = ninguna)
 *   REGISTROS           0        cuentas nuevas por la API (0 = ninguna)
 *   TASA_REGISTROS      50       registros por segundo; el techo medido en un PC de 12 hilos es ≈ 160/s, lo pone el BCrypt
 *
 * PRERREQUISITOS: el backend con el perfil `carga` (docker-compose.carga.yml), que vacía el límite por IP: k6 sale de
 * una sola IP y sin eso mediría el 429 del limitador. `scripts/carga/demo.mjs` lo prepara todo y lanza esto dentro de
 * la red del compose (el reenvío de puertos de Docker Desktop se satura antes que el backend, ver el README).
 *
 * Umbrales: RNF002 (p95 de un reporte < 1 s) y menos de 1 % de errores. Un 429 no cuenta como error: es el cupo por
 * dispositivo (RF006) o el tope de SSE respondiendo bien, y se cuenta aparte en `reportes_limitados`.
 */
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const USUARIOS = Number(__ENV.USUARIOS || 30000);
const VENTANA = Number(__ENV.VENTANA || 60);
const FOCOS = Number(__ENV.FOCOS || 12);
const PORCENTAJE_FOCOS = Number(__ENV.PORCENTAJE_FOCOS || 0.85);
const CONFIRMAR = Number(__ENV.CONFIRMAR || 0.15);
const CON_COORDENADA = Number(__ENV.CON_COORDENADA || 0.6);
const LECTORES = Number(__ENV.LECTORES || 150);
const VEEDORES_TASA = Number(__ENV.VEEDORES_TASA || 2);
const CLAVE_VEEDORES = __ENV.CLAVE_VEEDORES || '';
if (VEEDORES_TASA > 0 && !CLAVE_VEEDORES) {
  throw new Error('Falta CLAVE_VEEDORES: la clave de demostración ya no está en el repositorio, usa la que imprimió el sembrador (o VEEDORES_TASA=0).');
}
const SUSCRIPCIONES = Number(__ENV.SUSCRIPCIONES || 1);
const VEEDORES = (__ENV.VEEDORES || '').split(',').map((c) => c.trim()).filter(Boolean);
const REGISTROS = Number(__ENV.REGISTROS || 0);
const TASA_REGISTROS = Number(__ENV.TASA_REGISTROS || 50);
// Dominio reservado: las cuentas de la carga se cuentan y se borran por él (agregar-usuarios.mjs no lo usa).
const DOMINIO_REGISTROS = 'carga.aguavigia.local';

const JSON_HEADERS = { 'Content-Type': 'application/json' };
let avisosDeFallo = 0;

// 429 y 409 son respuestas correctas del sistema (cupo por dispositivo, reporte ya confirmado), no fallos.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409, 429));

const reportesAceptados = new Counter('reportes_aceptados');
const reportesLimitados = new Counter('reportes_limitados');
const reportesEnFocos = new Counter('reportes_en_focos');
const reportesFallidos = new Counter('reportes_fallidos');
const confirmacionesAceptadas = new Counter('confirmaciones_aceptadas');
const sesionesVeedor = new Counter('sesiones_veedor');
const suscripcionesCreadas = new Counter('suscripciones_creadas');
const registrosAceptados = new Counter('registros_aceptados');
const registrosFallidos = new Counter('registros_fallidos');

const escenarios = {
    reportes: {
        executor: 'constant-arrival-rate',
        // USUARIOS iteraciones repartidas en VENTANA segundos: cada iteración es un vecino.
        rate: USUARIOS,
        timeUnit: `${VENTANA}s`,
        duration: `${VENTANA}s`,
        // VUs de sobra desde el principio: crearlos bajo demanda tarda y k6 descarta las iteraciones que llegan mientras tanto
        // (medido: 249 descartadas de 30 000 con solo 125 precargados).
        preAllocatedVUs: Math.min(3000, Math.max(100, Math.ceil((USUARIOS / VENTANA) * 1.2))),
        maxVUs: 6000,
        exec: 'vecino',
    },
    lectores: {
        executor: 'constant-arrival-rate',
        rate: LECTORES,
        timeUnit: '1s',
        duration: `${VENTANA}s`,
        preAllocatedVUs: Math.max(20, Math.ceil(LECTORES / 5)),
        maxVUs: 1500,
        exec: 'lector',
    },
};
if (VEEDORES.length > 0 && VEEDORES_TASA > 0) {
    escenarios.veedores = {
        executor: 'constant-arrival-rate',
        rate: VEEDORES_TASA,
        timeUnit: '1s',
        duration: `${VENTANA}s`,
        preAllocatedVUs: 10,
        maxVUs: 200,
        exec: 'veedor',
    };
}
if (SUSCRIPCIONES > 0) {
    escenarios.suscripciones = {
        executor: 'constant-arrival-rate',
        rate: SUSCRIPCIONES,
        timeUnit: '1s',
        duration: `${VENTANA}s`,
        preAllocatedVUs: 5,
        maxVUs: 100,
        exec: 'suscriptor',
    };
}

if (REGISTROS > 0) {
    escenarios.registros = {
        executor: 'constant-arrival-rate',
        rate: TASA_REGISTROS,
        timeUnit: '1s',
        // Su propia duración: a ≈ 160/s como techo, 20 000 altas no caben en la ventana de 60 s de los reportes.
        duration: `${Math.max(1, Math.ceil(REGISTROS / TASA_REGISTROS))}s`,
        // Cada alta dura al menos 100 ms (RNF024) y bajo carga llega a varios cientos: hacen falta muchos VUs.
        preAllocatedVUs: Math.min(1500, Math.max(50, TASA_REGISTROS * 2)),
        maxVUs: 3000,
        exec: 'registro',
    };
}

export const options = {
    scenarios: escenarios,
    thresholds: {
        // RNF002: el 95 % de los reportes se registra en menos de 1 s, también con la avería masiva en marcha.
        'http_req_duration{grupo:reporte}': ['p(95)<1000'],
        'http_req_duration{grupo:lectura}': ['p(95)<1000'],
        // Un alta cifra la clave con BCrypt y dura al menos 100 ms a propósito: su umbral es más holgado.
        ...(REGISTROS > 0 ? { 'http_req_duration{grupo:registro}': ['p(95)<2000'] } : {}),
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
        // Si el generador no da abasto, las iteraciones se descartan: eso es del banco de pruebas, y se ve aquí.
        dropped_iterations: [`count<${Math.ceil(USUARIOS * 0.01)}`],
    },
    // No guardar el detalle de cada petición en memoria: a 30 000 reportes pesaría más que la propia prueba.
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

/** Punto dentro del polígono (trazado de rayos) para que la coordenada de un reporte caiga de verdad en su barrio. */
function dentro(punto, anillo) {
    let adentro = false;
    for (let i = 0, j = anillo.length - 1; i < anillo.length; j = i++) {
        const [xi, yi] = anillo[i];
        const [xj, yj] = anillo[j];
        if (yi > punto[1] !== yj > punto[1] && punto[0] < ((xj - xi) * (punto[1] - yi)) / (yj - yi) + xi) {
            adentro = !adentro;
        }
    }
    return adentro;
}

function anilloExterior(geometria) {
    if (geometria.type === 'Polygon') return geometria.coordinates[0];
    // MultiPolygon: el de más vértices es, en la práctica, el cuerpo principal del barrio.
    return geometria.coordinates.map((poligono) => poligono[0]).sort((a, b) => b.length - a.length)[0];
}

function puntoInterior(geometria) {
    const anillo = anilloExterior(geometria);
    const xs = anillo.map((p) => p[0]);
    const ys = anillo.map((p) => p[1]);
    const [minX, maxX, minY, maxY] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
    const centro = [(minX + maxX) / 2, (minY + maxY) / 2];
    if (dentro(centro, anillo)) return centro;
    for (let intento = 0; intento < 400; intento++) {
        const candidato = [minX + Math.random() * (maxX - minX), minY + Math.random() * (maxY - minY)];
        if (dentro(candidato, anillo)) return candidato;
    }
    return anillo[0];
}

export function setup() {
    const respuestaSectores = http.get(`${BASE_URL}/api/sectores`);
    if (respuestaSectores.status !== 200) {
        throw new Error(`No se pudo leer /api/sectores (status ${respuestaSectores.status}). ¿Está el backend arriba?`);
    }
    const sectores = JSON.parse(respuestaSectores.body).sectores;
    if (sectores.length === 0) {
        throw new Error('El backend no tiene sectores sembrados. Corre scripts/sembrar-sectores.mjs antes.');
    }

    const geometria = JSON.parse(http.get(`${BASE_URL}/api/sectores/geometria`).body).features;
    const puntos = {};
    for (const feature of geometria) {
        puntos[feature.id] = puntoInterior(feature.geometry);
    }

    // Los focos son barrios que hoy NO están sin servicio, para que la avería masiva cambie algo a la vista.
    const candidatos = sectores
        .filter((s) => s.estado !== 'SIN_SERVICIO' && puntos[s.id])
        .sort((a, b) => (a.id < b.id ? -1 : 1));
    const conDato = candidatos.filter((s) => s.poblacion && s.poblacion >= 800 && s.poblacion <= 20000);
    const paso = Math.max(1, Math.floor(conDato.length / Math.max(FOCOS, 1)));
    const focos = [];
    for (let i = 0; i < conDato.length && focos.length < FOCOS; i += paso) focos.push(conDato[i].id);
    for (let i = 0; i < candidatos.length && focos.length < FOCOS; i++) {
        if (!focos.includes(candidatos[i].id)) focos.push(candidatos[i].id);
    }

    return {
        corrida: (__ENV.CORRIDA || Date.now().toString(36)),
        ids: sectores.map((s) => s.id),
        puntos,
        focos,
    };
}

function huella(data, prefijo, indice) {
    // La API exige entre 32 y 128 caracteres.
    return `${prefijo}-${data.corrida}-${indice}-${Math.random().toString(36).slice(2, 10)}`.padEnd(40, '0');
}

function elegir(lista) {
    return lista[Math.floor(Math.random() * lista.length)];
}

function tipoDisperso() {
    const azar = Math.random();
    if (azar < 0.5) return 'SIN_AGUA';
    if (azar < 0.8) return 'PRESION_BAJA';
    return 'SERVICIO_RESTABLECIDO';
}

/** Cuántos focos ya se encendieron: se van sumando a lo largo del 70 % inicial de la ventana. */
function focosActivos(data, indice) {
    if (data.focos.length === 0) return 0;
    const segundos = (indice * VENTANA) / USUARIOS;
    const pasoSegundos = (VENTANA * 0.7) / data.focos.length;
    return Math.min(data.focos.length, Math.floor(segundos / pasoSegundos) + 1);
}

export function vecino(data) {
    const indice = exec.scenario.iterationInTest;
    // k6 lanza una iteración de más por redondeo en el borde de la ventana: cada vecino reporta una sola vez.
    if (indice >= USUARIOS) return;
    const activos = focosActivos(data, indice);
    const enFoco = activos > 0 && Math.random() < PORCENTAJE_FOCOS;
    const sectorId = enFoco ? data.focos[Math.floor(Math.random() * activos)] : elegir(data.ids);
    // En una avería masiva casi todo el mundo reporta lo mismo.
    const tipo = enFoco ? 'SIN_AGUA' : tipoDisperso();

    const cuerpo = { sectorId, tipo, huella: huella(data, 'vec', indice) };
    if (Math.random() < CON_COORDENADA && data.puntos[sectorId]) {
        cuerpo.coordenada = { latitud: data.puntos[sectorId][1], longitud: data.puntos[sectorId][0] };
    }
    const respuesta = http.post(`${BASE_URL}/api/reportes`, JSON.stringify(cuerpo), {
        headers: JSON_HEADERS,
        tags: { grupo: 'reporte' },
    });

    if (respuesta.status === 429) {
        reportesLimitados.add(1);
        return;
    }
    const registrado = check(respuesta, { 'el reporte se registró (201)': (r) => r.status === 201 });
    if (!registrado) {
        reportesFallidos.add(1);
        // Solo los dos primeros de cada VU: a 30 000 reportes, un aviso por fallo taparía todo lo demás.
        if (avisosDeFallo++ < 2) {
            console.warn(`reporte fallido: status=${respuesta.status} cuerpo=${String(respuesta.body).slice(0, 200)}`);
        }
        return;
    }
    reportesAceptados.add(1);
    if (enFoco) reportesEnFocos.add(1);

    if (Math.random() < CONFIRMAR) {
        const id = respuesta.json('id');
        const confirmacion = http.post(
            `${BASE_URL}/api/reportes/${id}/confirmar`,
            JSON.stringify({ huella: huella(data, 'conf', indice) }),
            { headers: JSON_HEADERS, tags: { grupo: 'confirmacion' } });
        if (check(confirmacion, { 'la confirmación se aceptó': (r) => r.status === 200 || r.status === 201 })) {
            confirmacionesAceptadas.add(1);
        }
    }
}

export function lector(data) {
    const azar = Math.random();
    let respuesta;
    if (azar < 0.7) {
        respuesta = http.get(`${BASE_URL}/api/sectores`, { tags: { grupo: 'lectura' } });
    } else if (azar < 0.9) {
        respuesta = http.get(`${BASE_URL}/api/sectores/${elegir(data.ids)}`, { tags: { grupo: 'lectura' } });
    } else {
        respuesta = http.get(`${BASE_URL}/api/bitacora?tamano=5`, { tags: { grupo: 'lectura' } });
    }
    check(respuesta, { 'la lectura respondió 200': (r) => r.status === 200 });
}

export function veedor() {
    const correo = elegir(VEEDORES);
    const sesion = http.post(`${BASE_URL}/api/veedor/sesion`, JSON.stringify({ correo, clave: CLAVE_VEEDORES }), {
        headers: JSON_HEADERS,
        tags: { grupo: 'sesion' },
    });
    if (!check(sesion, { 'el veedor inició sesión': (r) => r.status === 200 })) return;
    sesionesVeedor.add(1);
    const yo = http.get(`${BASE_URL}/api/veedor/yo`, {
        headers: { Authorization: `Bearer ${sesion.json('token')}` },
        tags: { grupo: 'sesion' },
    });
    check(yo, { 'el panel del veedor responde 200': (r) => r.status === 200 });
}

export function suscriptor(data) {
    const sectorIds = [elegir(data.ids)];
    const respuesta = http.post(
        `${BASE_URL}/api/suscripciones`,
        JSON.stringify({ correo: `carga-${data.corrida}-${__VU}-${__ITER}@carga.invalid`, sectorIds }),
        { headers: JSON_HEADERS, tags: { grupo: 'suscripcion' } });
    if (check(respuesta, { 'la suscripción se creó': (r) => r.status >= 200 && r.status < 300 })) {
        suscripcionesCreadas.add(1);
    }
}

const NOMBRES_REGISTRO = ['Ana', 'Luis', 'María', 'Carlos', 'Daniela', 'Jorge', 'Valentina', 'Andrés', 'Camila', 'Rafael', 'Yuleidis', 'Keiner'];
const APELLIDOS_REGISTRO = ['Pérez', 'Gómez', 'Marrugo', 'Julio', 'Padilla', 'Castro', 'Barrios', 'Mercado', 'Herrera', 'Ospino', 'Díaz', 'Arrieta'];

export function registro(data) {
    const indice = exec.scenario.iterationInTest;
    if (indice >= REGISTROS) return;
    const cuerpo = {
        correo: `r-${data.corrida}-${indice}@${DOMINIO_REGISTROS}`.toLowerCase(),
        nombre: `${elegir(NOMBRES_REGISTRO)} ${elegir(APELLIDOS_REGISTRO)} ${elegir(APELLIDOS_REGISTRO)}`,
        clave: CLAVE_VEEDORES,
        barrioId: elegir(data.ids),
    };
    const respuesta = http.post(`${BASE_URL}/api/cuentas/registro`, JSON.stringify(cuerpo), {
        headers: JSON_HEADERS,
        tags: { grupo: 'registro' },
    });
    if (check(respuesta, { 'el registro se aceptó (202)': (r) => r.status === 202 })) {
        registrosAceptados.add(1);
    } else {
        registrosFallidos.add(1);
        if (avisosDeFallo++ < 2) console.warn(`registro fallido: status=${respuesta.status} cuerpo=${String(respuesta.body).slice(0, 200)}`);
    }
}
