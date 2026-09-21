/**
 * Escala — escritura de reportes ciudadanos (ADR-049).
 *
 * Dos escenarios:
 *   - `repartidos`: reportes de vecinos distintos en sectores distintos. Es el tráfico normal.
 *   - `pico_un_sector`: una avería masiva, todos reportando el MISMO sector a la vez. Ejercita la
 *     evaluación de consenso bajo concurrencia (dos POST simultáneos no deben duplicar el evento).
 *
 * Uso:
 *   docker run --rm -i --network host -e BASE_URL=http://localhost:8081 grafana/k6 run - \
 *       < scripts/carga/escritura-reportes.js
 *
 * PRERREQUISITO: vaciar `aguavigia.rate-limit.reglas` en el entorno de prueba. Ese límite es por IP
 * (30 por minuto en /api/reportes/**) y k6 sale de una sola IP: sin vaciarlo verás 429 del limitador
 * y no la latencia real. Nunca se hace en producción.
 *
 * Cada solicitud usa una huella distinta (un vecino distinto), porque RF006 limita por dispositivo.
 * Tras la prueba se puede contar cuántos eventos de consenso salieron con:
 *   db.eventos_bitacora.countDocuments({ sectorId: "<el sector del pico>" })
 * y comprobar que no hay duplicados del mismo cambio de estado.
 */
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const TASA = Number(__ENV.TASA || 100);
const TIPOS = ['SIN_AGUA', 'PRESION_BAJA', 'SERVICIO_RESTABLECIDO'];

export const options = {
    scenarios: {
        repartidos: {
            executor: 'constant-arrival-rate',
            rate: TASA,
            timeUnit: '1s',
            duration: '2m',
            preAllocatedVUs: 100,
            maxVUs: 1000,
            exec: 'repartidos',
        },
        pico_un_sector: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: 1000,
            startTime: '30s',
            stages: [
                { target: TASA * 3, duration: '10s' },
                { target: TASA * 3, duration: '20s' },
                { target: 0, duration: '10s' },
            ],
            exec: 'picoUnSector',
        },
    },
    thresholds: {
        // RNF002: 95 % de las confirmaciones por debajo de 1 s, también durante el pico.
        'http_req_duration{expected_response:true}': ['p(95)<1000'],
        // 201 registrado; 429 por cupo RF006 es respuesta correcta, no un fallo del servidor.
        'http_req_failed{expected_response:false}': ['rate<0.05'],
        checks: ['rate>0.99'],
    },
};

export function setup() {
    const respuesta = http.get(`${BASE_URL}/api/sectores`);
    if (respuesta.status !== 200) {
        throw new Error(`No se pudo obtener /api/sectores (status ${respuesta.status})`);
    }
    const ids = JSON.parse(respuesta.body).sectores.map((s) => s.id);
    if (ids.length === 0) {
        throw new Error('El backend no tiene sectores sembrados. Corre scripts/sembrar-sectores.mjs antes.');
    }
    return { ids, sectorDelPico: ids[0] };
}

function reportar(sectorId, tipo) {
    // padEnd: la API exige una huella de entre 32 y 128 caracteres.
    const huella = `k6-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).slice(2)}`.padEnd(40, '0');
    const respuesta = http.post(
        `${BASE_URL}/api/reportes`,
        JSON.stringify({ sectorId, tipo, huella }),
        { headers: { 'Content-Type': 'application/json' } });
    check(respuesta, { 'respondió 201 (registrado)': (r) => r.status === 201 });
}

export function repartidos(data) {
    reportar(
        data.ids[Math.floor(Math.random() * data.ids.length)],
        TIPOS[Math.floor(Math.random() * TIPOS.length)]);
}

export function picoUnSector(data) {
    // En una avería masiva casi todo el mundo reporta lo mismo: SIN_AGUA.
    reportar(data.sectorDelPico, 'SIN_AGUA');
}
