// Detecta si la base ya guarda datos que alguien generó de verdad (reportes, cortes, propuestas de ingesta o cuentas que no
// son de demostración). La prueba de carga restaura un respaldo con `mongorestore --drop` y reinicia el backend con el perfil
// `carga`, así que contra una base con datos reales puede destruirlos o mezclarlos con 30 000 reportes sintéticos.

// Qué cuenta como real en cada colección. En `usuarios` no cuentan las sintéticas ni las de panel de demostración (ambas llevan
// `datosDeDemostracion`) ni el ADMIN inicial, que existe siempre.
const COLECCIONES = [
  { coleccion: 'reportes', filtro: {} },
  { coleccion: 'cortes', filtro: {} },
  { coleccion: 'propuestas_ingesta', filtro: {} },
  { coleccion: 'usuarios', filtro: { datosDeDemostracion: { $ne: true }, rol: { $ne: 'ADMIN' } } },
];

export async function buscarDatosReales(db) {
  const hallazgos = [];
  for (const { coleccion, filtro } of COLECCIONES) {
    const cantidad = await db.collection(coleccion).countDocuments(filtro);
    if (cantidad > 0) hallazgos.push({ coleccion, cantidad });
  }
  return hallazgos;
}

export function mensajeDatosReales(hallazgos) {
  const encontrado = hallazgos.map((h) => `  - ${h.coleccion}: ${h.cantidad}`).join('\n');
  return `La base ya tiene datos que no son de demostración:\n${encontrado}\n\n`
    + 'La prueba de carga los pondría en riesgo:\n'
    + '  - el respaldo se restaura con `mongorestore --drop` (--restaurar), que borra cada colección antes de repoblarla;\n'
    + '  - el perfil `carga` reinicia el backend sin límite de peticiones por IP y deja decenas de miles de reportes sintéticos\n'
    + '    mezclados con los reales, y mueve los estados de los barrios.\n'
    + 'Si es una base de pruebas y no te importa, repite con --sobre-datos-reales.';
}
