/**
 * Script para sembrar cortes de agua y reportes ciudadanos históricos (Mayo - Julio 2026).
 * Tarea del Sprint 6 (DevOps/QA).
 *
 * NO corre en el arranque (ADR-094): son datos inventados y la instancia real no los lleva. Queda para quien los pida a mano
 * (las pruebas E2E del frontend). El histórico real sale de los boletines de Acuacar.
 *
 * Inserta cortes inventados con origen OFICIAL_ACUACAR: en una base real eso contradeciria la regla 4 de la etica de datos (nada
 * llega al mapa sin la frase del boletin). Por eso se niega a correr contra una base que no es local (--permitir-remoto) o que
 * ya guarda datos que no son de demostracion (--sobre-datos-reales), y marca lo que inserta con `datosDeDemostracion`.
 *
 * Uso:
 *   cd scripts && npm install
 *   MONGODB_URI="mongodb://localhost:27017/?directConnection=true" node sembrar-historico-cortes.mjs
 */

import { MongoClient } from 'mongodb';
import { motivoParaNoContinuar } from './lib/guarda-destructiva.mjs';
import { parseArgs } from 'node:util';

// directConnection=true: Mongo local es un replica set de un nodo; sin esto el driver
// descubre que el miembro se anuncia como `mongo:27017` (nombre solo resoluble dentro de
// Docker) e intenta reconectarse ahi.
const MONGODB_URI = process.env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true';
const DB_NAME = process.env.MONGODB_DB ?? 'aguavigia';
const { values } = parseArgs({
  options: {
    'permitir-remoto': { type: 'boolean', default: false },
    'sobre-datos-reales': { type: 'boolean', default: false },
  },
});

// Rango de fechas: Mayo 1, 2026 a Julio 31, 2026
const START_DATE = new Date('2026-05-01T00:00:00Z').getTime();
const END_DATE = new Date('2026-07-31T23:59:59Z').getTime();

function randomItem(array) {
  return array[Math.floor(Math.random() * array.length)];
}

function generateRandomHash() {
  return Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
}

async function main() {
  const client = new MongoClient(MONGODB_URI);
  try {
    await client.connect();
    const db = client.db(DB_NAME);
    const sectoresCol = db.collection('sectores');
    const cortesCol = db.collection('cortes');
    const reportesCol = db.collection('reportes');

    const motivo = await motivoParaNoContinuar({ uri: MONGODB_URI, db,
      accion: 'borrar e inventar cortes y reportes historicos (mayo a julio de 2026)',
      permitirRemoto: values['permitir-remoto'], sobreDatosReales: values['sobre-datos-reales'] });
    if (motivo) {
      console.error(motivo);
      process.exit(1);
    }

    const sectores = await sectoresCol.find({}).project({ slug: 1, geometry: 1 }).toArray();
    if (sectores.length === 0) {
      console.error('Error: No hay sectores en la base de datos. Ejecuta sembrar-sectores.mjs primero.');
      process.exit(1);
    }

    const slugs = sectores.map(s => s.slug);
    
    // ---------------------------------------------------------
    // 1. Generar Cortes Históricos
    // ---------------------------------------------------------
    const numCortes = 120; 
    const cortes = [];
    const causas = [
      'Mantenimiento preventivo de red', 
      'Rotura de tubo matriz', 
      'Fallo eléctrico en estación de bombeo', 
      'Niveles bajos en represa', 
      'Reparación de urgencia'
    ];
    const origenes = ['OFICIAL_ACUACAR', 'INGESTA_IA', 'VEEDOR'];

    for (let i = 0; i < numCortes; i++) {
      const numSectoresAfectados = Math.floor(Math.random() * 6) + 1;
      const afectados = [];
      for(let j = 0; j < numSectoresAfectados; j++) {
        afectados.push(randomItem(slugs));
      }
      const afectadosUnicos = [...new Set(afectados)];

      const inicioTime = START_DATE + Math.random() * (END_DATE - START_DATE);
      const inicio = new Date(inicioTime);
      
      const duracionMs = (Math.floor(Math.random() * 48) + 2) * 60 * 60 * 1000;
      const finPrometido = new Date(inicioTime + duracionMs);
      
      const variacionMs = (Math.random() * 8 - 4) * 60 * 60 * 1000;
      let finReal = new Date(inicioTime + duracionMs + variacionMs);
      if (finReal < inicio) {
        finReal = new Date(inicioTime + 60 * 60 * 1000);
      }

      cortes.push({
        sectoresAfectados: afectadosUnicos,
        inicio,
        finPrometido,
        finReal,
        causa: randomItem(causas),
        origen: randomItem(origenes),
        estado: 'RESTABLECIDO',
        datosDeDemostracion: true
      });
    }

    await cortesCol.deleteMany({
      inicio: { $gte: new Date(START_DATE), $lte: new Date(END_DATE) }
    });
    
    if (cortes.length > 0) {
      await cortesCol.insertMany(cortes);
    }
    console.log(`✅ Sembrados ${cortes.length} cortes de agua (Mayo - Julio 2026).`);

    // ---------------------------------------------------------
    // 2. Generar Reportes Ciudadanos
    // ---------------------------------------------------------
    const numReportes = 600;
    const reportes = [];
    const tiposReporte = ['SIN_AGUA', 'PRESION_BAJA', 'SERVICIO_RESTABLECIDO'];
    const estadosModeracion = ['APROBADO', 'PENDIENTE', 'DESCARTADO'];

    for (let i = 0; i < numReportes; i++) {
      const sector = randomItem(sectores);
      const timestamp = new Date(START_DATE + Math.random() * (END_DATE - START_DATE));
      
      let latitud = 10.3910;
      let longitud = -75.4794;
      if (sector.geometry && sector.geometry.coordinates && sector.geometry.coordinates[0]) {
        try {
           const coords = sector.geometry.type === 'Polygon' ? sector.geometry.coordinates[0][0] : sector.geometry.coordinates[0][0][0];
           if (coords && coords.length === 2) {
             longitud = coords[0] + (Math.random() * 0.002 - 0.001);
             latitud = coords[1] + (Math.random() * 0.002 - 0.001);
           }
        } catch (e) {
           // Ignorar si el geojson no tiene el formato exacto
        }
      }

      reportes.push({
        sectorId: sector.slug,
        tipo: randomItem(tiposReporte),
        latitud,
        longitud,
        huella: generateRandomHash(),
        timestamp,
        estadoModeracion: randomItem(estadosModeracion),
        datosDeDemostracion: true
      });
    }

    await reportesCol.deleteMany({
      timestamp: { $gte: new Date(START_DATE), $lte: new Date(END_DATE) }
    });

    if (reportes.length > 0) {
      await reportesCol.insertMany(reportes);
    }
    console.log(`✅ Sembrados ${reportes.length} reportes ciudadanos históricos (Mayo - Julio 2026).`);
    console.log('🎉 Siembra de datos históricos completada exitosamente.');

  } catch (error) {
    console.error('Error durante la siembra histórica:', error);
  } finally {
    await client.close();
  }
}

main().catch(console.error);
