// Fixtures de las pruebas E2E del frontend: pisa el estado de tres barrios y borra los reportes de Arroyo Grande. Se niega a
// correr contra una base que no es local (--permitir-remoto) o que ya guarda datos reales (--sobre-datos-reales).
import { MongoClient } from 'mongodb';
import { parseArgs } from 'node:util';
import { motivoParaNoContinuar } from './lib/guarda-destructiva.mjs';

const { values } = parseArgs({
  options: {
    'permitir-remoto': { type: 'boolean', default: false },
    'sobre-datos-reales': { type: 'boolean', default: false },
  },
});
const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const cliente = new MongoClient(MONGODB_URI);
await cliente.connect();
try {
  const base = cliente.db(process.env.MONGODB_DB ?? 'aguavigia');
  const motivo = await motivoParaNoContinuar({ uri: MONGODB_URI, db: base,
    accion: 'dejar los barrios de las pruebas del frontend y borrar los reportes de arroyo-grande',
    permitirRemoto: values['permitir-remoto'], sobreDatosReales: values['sobre-datos-reales'] });
  if (motivo) {
    console.error(motivo);
    process.exit(1);
  }
  const sectores = base.collection('sectores');
  if (await sectores.countDocuments() !== 211) throw new Error('Las pruebas requieren los 211 sectores de la siembra local');
  const viejo = new Date(Date.now() - 48 * 60 * 60 * 1000);
  for (const [slug, valores] of [
    ['zona-industrial', { estadoActual: null, estadoActualizadoEn: null, estadoVerificadoEn: null, poblacion: null }],
    ['alameda-la-victoria', { estadoActual: 'CON_SERVICIO', estadoActualizadoEn: viejo, estadoVerificadoEn: viejo }],
    ['arroyo-grande', { estadoActual: 'CON_SERVICIO', estadoActualizadoEn: viejo, estadoVerificadoEn: viejo, poblacion: 1000 }],
  ]) {
    const cambio = await sectores.updateOne({ slug }, { $set: valores });
    if (cambio.matchedCount !== 1) throw new Error(`No se encontró el sector ${slug}`);
  }
  await base.collection('reportes').deleteMany({ sectorId: 'arroyo-grande' });
  process.stdout.write('Fixtures locales: zona-industrial sin estado ni censo, Alameda con 48 h, Arroyo Grande sin votos previos y umbral 3.\n');
} finally { await cliente.close(); }
