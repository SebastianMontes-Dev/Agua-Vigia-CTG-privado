import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import openapiTS, { astToString } from 'openapi-typescript'

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const contrato = resolve(raiz, '../backend/openapi.yaml')
const destino = resolve(raiz, 'src/api/generado/esquema.ts')

const cabecera =
  '// Generado desde backend/openapi.yaml con `npm run api:sync`. No se edita a mano.\n\n'

const modo = process.argv[2]
if (modo !== '--escribir' && modo !== '--comprobar') {
  console.error('Uso: node scripts/contrato-api.mjs --escribir | --comprobar')
  process.exit(2)
}

const generado = cabecera + astToString(await openapiTS(pathToFileURL(contrato)))

if (modo === '--escribir') {
  await mkdir(dirname(destino), { recursive: true })
  await writeFile(destino, generado)
  process.stdout.write(`Tipos del contrato escritos en ${destino}\n`)
} else {
  const actual = await readFile(destino, 'utf8').catch(() => null)
  if (actual !== generado) {
    console.error(
      'Los tipos de src/api/generado/esquema.ts no coinciden con backend/openapi.yaml.\n' +
        'Corre `npm run api:sync` y versiona el resultado.',
    )
    process.exit(1)
  }
  process.stdout.write('Los tipos del frontend coinciden con el contrato.\n')
}
