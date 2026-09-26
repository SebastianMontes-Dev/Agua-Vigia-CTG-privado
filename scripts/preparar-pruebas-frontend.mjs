import { MongoClient } from 'mongodb';
const cliente = new MongoClient(process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true');
await cliente.connect();
try {
  const base = cliente.db('aguavigia');
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
