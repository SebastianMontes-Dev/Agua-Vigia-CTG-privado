#!/usr/bin/env node
/**
 * Escala — comprueba que la micro-caché de nginx funciona CON EL BACKEND REAL (BUG-085).
 *
 * Por qué existe: el backend responde `Cache-Control: no-cache, no-store` (valor por defecto de Spring
 * Security) y nginx respeta la cabecera del origen, así que la caché nunca guardaba nada y las lecturas
 * públicas llegaban todas al backend. Una prueba con un backend de mentira no lo detectó; esta sí, porque
 * va contra el conjunto desplegado.
 *
 * Uso (contra el proxy, con el backend detrás y sectores sembrados):
 *   node scripts/carga/verificar-cache-proxy.mjs                      # http://localhost
 *   node scripts/carga/verificar-cache-proxy.mjs --url http://localhost:8090
 *
 * Sale con código 1 si falla alguna comprobación. Ojo: nginx limita a 30 peticiones/s por IP; este
 * script hace unas 12, no llega.
 */
import { parseArgs } from 'node:util';

const { values } = parseArgs({ options: { url: { type: 'string', default: 'http://localhost' } } });
const base = values.url.replace(/\/$/, '');
let fallos = 0;

function comprobar(descripcion, cumple, detalle = '') {
    console.log(`${cumple ? 'OK   ' : 'FALLA'}  ${descripcion}${cumple ? '' : `  -> ${detalle}`}`);
    if (!cumple) fallos++;
}

async function pedir(ruta, cabeceras = {}) {
    const r = await fetch(base + ruta, { headers: cabeceras });
    await r.arrayBuffer();
    return {
        estado: r.status,
        cache: r.headers.get('x-cache-status'),
        control: r.headers.get('cache-control'),
    };
}

// Una ruta con un parámetro que nadie más usa, para no depender de lo que ya hubiera en la caché.
const ruta = `/api/bitacora?pagina=0&tamano=${10 + Math.floor(Math.random() * 1000)}`;
const primera = await pedir(ruta);
const segunda = await pedir(ruta);
comprobar('la primera lectura pública es MISS', primera.cache === 'MISS', `X-Cache-Status=${primera.cache}`);
comprobar('la segunda lectura pública es HIT', segunda.cache === 'HIT', `X-Cache-Status=${segunda.cache}`);
comprobar(
    'un solo Cache-Control, público y de 5 s (no el no-store del origen)',
    segunda.control === 'public, max-age=5, stale-while-revalidate=30',
    `Cache-Control=${segunda.control}`,
);

const inexistente = '/api/sectores/no-existe-' + Math.floor(Math.random() * 1e6);
const e1 = await pedir(inexistente);
const e2 = await pedir(inexistente);
comprobar('un 404 no se cachea', e1.estado === 404 && e2.estado === 404 && e2.cache !== 'HIT', `estados ${e1.estado}/${e2.estado}, cache=${e2.cache}`);

const conCredenciales = await pedir('/api/sectores', { Authorization: 'Bearer prueba' });
comprobar('una petición con Authorization salta la caché', conCredenciales.cache === 'BYPASS', `X-Cache-Status=${conCredenciales.cache}`);

const panel = await pedir('/api/veedor/cortes');
comprobar('el panel nunca se cachea ni lleva X-Cache-Status', panel.cache === null && /no-store/.test(panel.control ?? ''), `cache=${panel.cache}, control=${panel.control}`);

console.log(fallos === 0 ? '\nLa micro-caché del proxy funciona.' : `\n${fallos} comprobación(es) fallida(s).`);
process.exit(fallos === 0 ? 0 : 1);
