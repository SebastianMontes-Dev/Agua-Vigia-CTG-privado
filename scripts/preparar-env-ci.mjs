import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

// .env efímero del CI. Desde ADR-086 el backend arranca sin .env (genera el secreto y la clave del ADMIN), pero el job de
// integración fija un secreto estable, un correo de ADMIN de prueba y el origen de la preview E2E.
const entorno = readFileSync('.env.example', 'utf8')
  .replace(/^CORS_ORIGENES=.*$/m, () => 'CORS_ORIGENES=http://localhost:4173')
  + `\nJWT_SECRET=${randomBytes(32).toString('base64')}\nADMIN_INICIAL_CORREO=admin@example.test\n`;
writeFileSync('.env', entorno);
process.stdout.write('Entorno local efímero generado; sin revelar credenciales.\n');
