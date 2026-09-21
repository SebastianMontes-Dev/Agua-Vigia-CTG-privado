/**
 * Escala — lectura pública (ADR-049). Es el tráfico dominante: miles de personas mirando el mapa.
 *
 * Mide que GET /api/sectores, /estadisticas, /cumplimiento y /bitacora aguanten una tasa alta de
 * peticiones con p95 < 300 ms y menos de 1 % de errores.
 *
 * Uso (sin instalar k6, con la imagen que ya trae Docker):
 *   docker run --rm -i --network host -e BASE_URL=http://localhost:8081 -e TASA=500 \
 *       grafana/k6 run - < scripts/carga/lectura-publica.js
 *   o con k6 instalado:  k6 run -e TASA=500 scripts/carga/lectura-publica.js
 *
 * IMPORTANTE — qué se está midiendo según a dónde apuntes:
 *   - Al backend directo (dev, :8081): mide el backend sin el micro-caché de nginx. Es la prueba dura.
 *   - Al proxy de producción (:80): mide el conjunto. Ojo: nginx limita a 30 peticiones/s por IP
 *     (`limit_req` en infra/nginx/nginx.conf) y k6 sale desde UNA sola IP, así que a partir de ahí
 *     verás 429 que no son del backend. Para medir el proxy a tasa alta hay que repartir la carga
 *     entre varias máquinas o subir ese límite en el entorno de prueba (nunca en producción).
 *
 * `TASA` es la tasa objetivo en peticiones por segundo. Desde una laptop, 500-2000 es lo realista;
 * los 5 000-10 000 de la meta salen de repartir la generación de carga entre varias máquinas.
 */
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const TASA = Number(__ENV.TASA || 500);

export const options = {
    scenarios: {
        lectura: {
            executor: 'ramping-arrival-rate',
            startRate: Math.max(10, Math.floor(TASA / 10)),
            timeUnit: '1s',
            preAllocatedVUs: 200,
            maxVUs: 3000,
            stages: [
                { target: Math.floor(TASA / 4), duration: '30s' },
                { target: TASA, duration: '1m' },
                { target: TASA, duration: '2m' },
                { target: 0, duration: '20s' },
            ],
        },
    },
    thresholds: {
        'http_req_duration{expected_response:true}': ['p(95)<300', 'p(99)<800'],
        http_req_failed: ['rate<0.01'],
    },
};

export function setup() {
    const respuesta = http.get(`${BASE_URL}/api/sectores`);
    if (respuesta.status !== 200) {
        throw new Error(`No se pudo obtener /api/sectores (status ${respuesta.status}) — ¿está el backend arriba?`);
    }
    const ids = JSON.parse(respuesta.body).sectores.map((s) => s.id);
    if (ids.length === 0) {
        throw new Error('El backend no tiene sectores sembrados. Corre scripts/sembrar-sectores.mjs antes.');
    }
    return { ids };
}

// Mezcla parecida a la real: casi todo el mundo pide el mapa; pocos abren estadísticas o un barrio.
const MEZCLA = [
    [0.6, () => '/api/sectores'],
    [0.15, () => '/api/estadisticas'],
    [0.1, () => '/api/cumplimiento'],
    [0.1, () => '/api/bitacora?pagina=0&tamano=20'],
];

export default function (data) {
    const azar = Math.random();
    let acumulado = 0;
    let ruta = null;
    for (const [peso, construir] of MEZCLA) {
        acumulado += peso;
        if (azar < acumulado) {
            ruta = construir();
            break;
        }
    }
    if (ruta === null) {
        ruta = `/api/sectores/${data.ids[Math.floor(Math.random() * data.ids.length)]}`;
    }

    const respuesta = http.get(`${BASE_URL}${ruta}`, { tags: { ruta: ruta.split('?')[0].replace(/\/[^/]+$/, '/{id}') } });
    check(respuesta, { 'respondió 200': (r) => r.status === 200 });
}
