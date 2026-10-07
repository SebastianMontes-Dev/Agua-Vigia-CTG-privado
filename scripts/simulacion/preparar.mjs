// Deja lista la clave de la simulación: genera SIMULACION_CLAVE en el .env de la raíz si falta. Nunca la imprime ni la sobrescribe; .env está
// ignorado por git (se comprueba aquí) y la clave no se versiona nunca.
//
//   node scripts/simulacion/preparar.mjs

import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { asegurarClaveEnEnv } from './lib/clave-env.mjs';

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
const RUTA_ENV = `${RAIZ}.env`;

function estaIgnorado() {
  try {
    execFileSync('git', ['check-ignore', '-q', '.env'], { cwd: RAIZ, stdio: 'ignore' });
    return true;
  } catch (error) {
    if (error.status === 1) return false; // git respondió: no está ignorado
    return null; // sin git o fuera de un repositorio (p. ej. dentro de la imagen del simulador): no se puede saber
  }
}

const ignorado = estaIgnorado();
if (ignorado === false) {
  console.error('ERROR: .env no está en .gitignore y la clave quedaría versionable. Añade «.env» a .gitignore y vuelve a correr esto.');
  process.exit(1);
}

const actual = existsSync(RUTA_ENV) ? readFileSync(RUTA_ENV, 'utf8') : null;
const { contenido, estado } = asegurarClaveEnEnv(actual, () => randomBytes(32).toString('hex'));

if (estado === 'existente') {
  console.log('SIMULACION_CLAVE ya está en .env: no se toca.');
} else if (estado === 'corta') {
  console.error('AVISO: SIMULACION_CLAVE existe en .env pero tiene menos de 32 caracteres y la API de simulación no arrancaría. No se sobrescribe: bórrala de .env y vuelve a correr esto.');
  process.exit(1);
} else {
  writeFileSync(RUTA_ENV, contenido, { mode: 0o600 });
  console.log(`SIMULACION_CLAVE generada y guardada en ${actual === null ? 'un .env nuevo' : '.env'} (no se muestra).`);
}
if (ignorado === null) console.log('(No pude comprobar con git que .env esté ignorado; revísalo.)');

console.log(`
Siguiente paso:
  docker compose --profile simulacion up -d --build backend-sim
  docker compose --profile simulacion run --rm simulador iniciar --velocidad 60
Guía completa: scripts/simulacion/README.md`);
