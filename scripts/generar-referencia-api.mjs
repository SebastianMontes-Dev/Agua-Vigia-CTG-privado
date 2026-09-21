#!/usr/bin/env node
/**
 * Genera docs/api/referencia-de-rutas.md desde el contrato y el código, para que la referencia no
 * pueda desviarse de lo que el backend expone de verdad.
 *
 *   1. Regenerar el contrato (deja también backend/target/openapi.json):
 *        cd backend && ./mvnw test -Dtest=ContratoOpenApiTest -Dopenapi.regenerar=true
 *   2. node scripts/generar-referencia-api.mjs
 *
 * De qué sale cada dato:
 *   - rutas, parámetros, cuerpos, respuestas y esquemas: backend/target/openapi.json
 *   - PERMISO exigido por cada ruta: las anotaciones @PreAuthorize de los controladores (OpenAPI no lo
 *     expresa). Una ruta de /api/veedor/** sin @PreAuthorize solo exige estar autenticado.
 *
 * Termina con código 1 si una ruta protegida del contrato no se pudo casar con su controlador: es la
 * señal de que el escaneo de anotaciones dejó de entender el código.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const CONTRATO = path.join(RAIZ, 'backend', 'target', 'openapi.json');
const CONTROLADORES = path.join(RAIZ, 'backend', 'src', 'main', 'java', 'com', 'aguavigia', 'ctg', 'api');
const SALIDA = path.join(RAIZ, 'docs', 'api', 'referencia-de-rutas.md');

if (!fs.existsSync(CONTRATO)) {
    console.error(`Falta ${path.relative(RAIZ, CONTRATO)}. Corre antes:\n  cd backend && ./mvnw test -Dtest=ContratoOpenApiTest -Dopenapi.regenerar=true`);
    process.exit(2);
}
const api = JSON.parse(fs.readFileSync(CONTRATO, 'utf8'));

const ANOTACION_A_VERBO = {
    GetMapping: 'GET', PostMapping: 'POST', PatchMapping: 'PATCH', PutMapping: 'PUT', DeleteMapping: 'DELETE',
};
const VERBOS_OPENAPI = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE']);

// ---------- 1. Permisos: escaneo de los controladores
const primeraCadena = (texto) => (/"([^"]*)"/.exec(texto) ?? [])[1] ?? '';
const permisoDe = (texto) => (/PERM_([A-Z_]+)/.exec(texto) ?? [])[1] ?? null;

/** @returns Map "VERBO /ruta" → permiso (string) o null si solo exige sesión. */
function escanearControladores() {
    const mapa = new Map();
    for (const archivo of fs.readdirSync(CONTROLADORES).filter((f) => f.endsWith('Controller.java'))) {
        const lineas = fs.readFileSync(path.join(CONTROLADORES, archivo), 'utf8').replace(/\r\n/g, '\n').split('\n');
        let base = '';
        let permisoClase = null;
        let acumulado = [];
        let enClase = false;

        for (const cruda of lineas) {
            const linea = cruda.trim();
            if (linea === '' || linea.startsWith('//') || linea.startsWith('*') || linea.startsWith('/*')) continue;
            acumulado.push(linea);

            if (!enClase) {
                if (/^public class /.test(linea)) {
                    const texto = acumulado.join(' ');
                    const rm = /@RequestMapping\(([^)]*)\)/.exec(texto);
                    if (rm) base = primeraCadena(rm[1]);
                    permisoClase = permisoDe((/@PreAuthorize\(([^)]*)\)/.exec(texto) ?? [])[1] ?? '');
                    enClase = true;
                    acumulado = [];
                }
                continue;
            }

            // Un método público: la firma cierra el bloque de anotaciones que la precede.
            if (/^public\s.*\(/.test(linea) || /^public\s.*\($/.test(linea)) {
                const texto = acumulado.join(' ');
                acumulado = [];
                for (const [anotacion, verbo] of Object.entries(ANOTACION_A_VERBO)) {
                    const mm = new RegExp(`@${anotacion}(?:\\(([^)]*)\\))?`).exec(texto);
                    if (!mm) continue;
                    const argumentos = mm[1] ? mm[1].split(/\bproduces\b|\bconsumes\b/)[0] : '';
                    const ruta = (base + primeraCadena(argumentos)).replace(/\/+$/, '') || '/';
                    const pm = /@PreAuthorize\(([^)]*)\)/.exec(texto);
                    mapa.set(`${verbo} ${ruta}`, (pm ? permisoDe(pm[1]) : null) ?? permisoClase);
                }
            } else if (linea === '}' || linea.endsWith(';') || linea.endsWith('{')) {
                // Fin de un miembro que no es un endpoint: se descartan sus anotaciones pendientes.
                acumulado = [];
            }
        }
    }
    return mapa;
}
const permisos = escanearControladores();

// ---------- 2. Utilidades sobre el contrato
const nombreDeRef = (ref) => ref.split('/').pop();
const ancla = (nombre) => nombre.toLowerCase().replace(/[^a-z0-9]+/g, '-');

function tipoLegible(esquema) {
    if (!esquema) return '—';
    if (esquema.$ref) return `[${nombreDeRef(esquema.$ref)}](#esquema-${ancla(nombreDeRef(esquema.$ref))})`;
    if (esquema.enum) return `enum(${esquema.enum.join(', ')})`;
    if (esquema.type === 'array') return `lista de ${tipoLegible(esquema.items)}`;
    if (esquema.type === 'object' && esquema.additionalProperties) return `mapa de ${tipoLegible(esquema.additionalProperties)}`;
    return esquema.format ? `${esquema.type} (${esquema.format})` : (esquema.type ?? 'objeto');
}

function unaLinea(texto, max = 240) {
    if (!texto) return '';
    const plano = String(texto).replace(/\s+/g, ' ').replace(/\|/g, '\\|').trim();
    return plano.length > max ? `${plano.slice(0, max - 1)}…` : plano;
}

function acceso(verbo, ruta) {
    if (!ruta.startsWith('/api/veedor/')) return 'Público';
    if (verbo === 'POST' && ruta === '/api/veedor/sesion') return 'Público (login)';
    const permiso = permisos.get(`${verbo} ${ruta}`);
    return permiso ? `Sesión + \`${permiso}\`` : 'Sesión (cualquier cuenta)';
}

function cuerpoDe(operacion) {
    const contenido = operacion.requestBody?.content;
    if (!contenido) return '—';
    const [tipo, valor] = Object.entries(contenido)[0];
    return `${tipoLegible(valor.schema)} (\`${tipo}\`)`;
}

function parametrosDe(operacion, itemPath) {
    const lista = [...(itemPath.parameters ?? []), ...(operacion.parameters ?? [])];
    if (lista.length === 0) return '—';
    return lista.map((p) => `\`${p.name}\` (${p.in}${p.required ? ', obligatorio' : ''})`).join('<br>');
}

// ---------- 3. Recorrer el contrato
const porTag = new Map();
const sinCasar = [];
let total = 0;
for (const [ruta, item] of Object.entries(api.paths)) {
    for (const [metodo, operacion] of Object.entries(item)) {
        const verbo = metodo.toUpperCase();
        if (!VERBOS_OPENAPI.has(verbo)) continue;
        total += 1;
        const etiqueta = operacion.tags?.[0] ?? 'Otros';
        if (!porTag.has(etiqueta)) porTag.set(etiqueta, []);
        porTag.get(etiqueta).push({ ruta, verbo, operacion, item });
        const protegida = ruta.startsWith('/api/veedor/') && !(verbo === 'POST' && ruta === '/api/veedor/sesion');
        if (protegida && !permisos.has(`${verbo} ${ruta}`)) sinCasar.push(`${verbo} ${ruta}`);
    }
}

// ---------- 4. Escribir el Markdown
const salida = [];
const p = (linea = '') => salida.push(linea);

p('# Referencia de rutas y esquemas');
p();
p('> **Generado — no editar a mano.** Lo produce `scripts/generar-referencia-api.mjs` a partir de');
p('> `backend/openapi.yaml` (rutas, cuerpos, respuestas, esquemas) y de los `@PreAuthorize` de los');
p('> controladores (permisos). Para regenerarlo, ver la cabecera del script.');
p('>');
p('> Las **guías** de esta carpeta explican el porqué y los flujos; esta página es el catálogo exacto.');
p();
p(`**${total} operaciones** en ${Object.keys(api.paths).length} rutas, más las páginas HTML de cortesía y el SSE.`);
p();
p('Leyenda de **Acceso**: *Público* no exige token · *Sesión + `PERMISO`* exige `Authorization: Bearer <token>` de una');
p('cuenta que tenga ese permiso · *Sesión (cualquier cuenta)* exige token pero ningún permiso concreto.');
p('Todo error sale en RFC 7807: ver [Errores y límites](errores-y-limites.md).');
p();

const etiquetas = [...porTag.keys()].sort((a, b) => a.localeCompare(b, 'es'));
p('## Índice');
p();
for (const etiqueta of etiquetas) p(`- [${etiqueta}](#${ancla(etiqueta)}) (${porTag.get(etiqueta).length})`);
p('- [Esquemas](#esquemas)');
p();

for (const etiqueta of etiquetas) {
    const definicion = (api.tags ?? []).find((t) => t.name === etiqueta);
    p(`## ${etiqueta}`);
    p();
    if (definicion?.description) {
        p(unaLinea(definicion.description, 400));
        p();
    }
    const operaciones = porTag.get(etiqueta).sort((a, b) => a.ruta.localeCompare(b.ruta) || a.verbo.localeCompare(b.verbo));
    for (const { ruta, verbo, operacion, item } of operaciones) {
        p(`### \`${verbo} ${ruta}\``);
        p();
        if (operacion.summary) p(`**${unaLinea(operacion.summary)}**`);
        if (operacion.summary) p();
        if (operacion.description) {
            p(String(operacion.description).replace(/\r\n/g, '\n').split('\n').map((l) => l.trim()).join(' ').replace(/\s+/g, ' ').trim());
            p();
        }
        p('| | |');
        p('|---|---|');
        p(`| **Acceso** | ${acceso(verbo, ruta)} |`);
        p(`| **Parámetros** | ${parametrosDe(operacion, item)} |`);
        p(`| **Cuerpo** | ${cuerpoDe(operacion)} |`);
        const respuestas = Object.entries(operacion.responses ?? {}).map(([codigo, r]) => {
            const contenido = r.content ? Object.entries(r.content)[0] : null;
            const forma = contenido ? ` → ${tipoLegible(contenido[1].schema)}` : '';
            return `\`${codigo}\` ${unaLinea(r.description, 120)}${forma}`;
        });
        p(`| **Respuestas** | ${respuestas.join('<br>') || '—'} |`);
        p();
    }
}

p('## Esquemas');
p();
p('Los tipos que viajan en cuerpos y respuestas. Un campo **nullable** puede llegar como `null`: significa «sin');
p('dato», no «valor por defecto».');
p();
const esquemas = Object.entries(api.components?.schemas ?? {}).sort(([a], [b]) => a.localeCompare(b));
for (const [nombre, esquema] of esquemas) {
    p(`<a id="esquema-${ancla(nombre)}"></a>`);
    p();
    p(`### ${nombre}`);
    p();
    if (esquema.description) {
        p(unaLinea(esquema.description, 500));
        p();
    }
    const propiedades = Object.entries(esquema.properties ?? {});
    if (propiedades.length === 0) {
        p(esquema.enum ? `Valores: ${esquema.enum.map((v) => `\`${v}\``).join(', ')}` : '_Sin propiedades declaradas._');
        p();
        continue;
    }
    const obligatorias = new Set(esquema.required ?? []);
    p('| Campo | Tipo | Oblig. | Nulo | Descripción |');
    p('|---|---|---|---|---|');
    for (const [campo, def] of propiedades) {
        p(`| \`${campo}\` | ${tipoLegible(def)} | ${obligatorias.has(campo) ? 'sí' : ''} | ${def.nullable ? 'sí' : ''} | ${unaLinea(def.description)} |`);
    }
    p();
}

fs.writeFileSync(SALIDA, `${salida.join('\n')}\n`);
console.log(`Escrito ${path.relative(RAIZ, SALIDA)}: ${total} operaciones, ${esquemas.length} esquemas.`);

if (sinCasar.length > 0) {
    console.error('\nRutas protegidas que el escaneo de controladores no pudo casar (se listan como «cualquier cuenta»):');
    for (const ruta of sinCasar) console.error(`  ${ruta}`);
    console.error('Si de verdad solo exigen sesión, ignora esto; si no, revisa el escaneo de anotaciones.');
    process.exit(1);
}
