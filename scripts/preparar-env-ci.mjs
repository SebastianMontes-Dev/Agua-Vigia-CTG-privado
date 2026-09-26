import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
const referencia = readFileSync('docs/ingenieria/entorno-local.md', 'utf8');
const hash = referencia.match(/\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}/)?.[0];
if (!hash) throw new Error('Falta el hash de desarrollo documentado');
const entorno = readFileSync('.env.example', 'utf8')
  .replace(/^JWT_SECRET=.*$/m, () => `JWT_SECRET=${randomBytes(32).toString('base64')}`)
  .replace(/^VEEDOR_PASSWORD_HASH=.*$/m, () => `VEEDOR_PASSWORD_HASH=${hash.replaceAll('$', '$$')}`)
  .replace(/^ADMIN_INICIAL_CORREO=.*$/m, () => 'ADMIN_INICIAL_CORREO=admin@example.test');
writeFileSync('.env', entorno);
process.stdout.write('Entorno local efímero generado; sin revelar credenciales.\n');
