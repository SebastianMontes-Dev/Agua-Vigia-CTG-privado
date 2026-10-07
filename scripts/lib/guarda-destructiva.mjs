// Guarda común de los scripts a mano que borran o pisan datos (sembrar sectores, histórico de cortes, fixtures del frontend).
// Sin ella, ejecutar uno contra la base real —por un `MONGODB_URI` heredado del entorno o por costumbre— deja los 211 barrios
// «sin datos» o mezcla cortes inventados con los que sí publicó Acuacar. Son dos permisos distintos: tocar una base que no es
// local (`--permitir-remoto`) y pisar una que ya guarda datos que no son de demostración (`--sobre-datos-reales`).
import { esBaseLocal } from './base-local.mjs';
import { buscarDatosReales } from './datos-reales.mjs';

/**
 * Devuelve `null` si se puede seguir, o el texto que hay que mostrar para negarse. `accion` es lo que el script iba a hacer
 * («borrar la colección sectores»), para que el mensaje diga qué se evita.
 */
export async function motivoParaNoContinuar({ uri, db, accion, permitirRemoto = false, sobreDatosReales = false }) {
  if (!permitirRemoto && !esBaseLocal(uri)) {
    return `Me niego a ${accion} en una base que no es local. Si es una base de pruebas, repite con --permitir-remoto.`;
  }
  if (sobreDatosReales) return null;
  const reales = await buscarDatosReales(db);
  if (reales.length === 0) return null;
  const encontrado = reales.map((h) => `  - ${h.coleccion}: ${h.cantidad}`).join('\n');
  return `Me niego a ${accion}: la base ya tiene datos que no son de demostración:\n${encontrado}\n`
    + 'Si es una base de pruebas y no te importa, repite con --sobre-datos-reales.';
}
