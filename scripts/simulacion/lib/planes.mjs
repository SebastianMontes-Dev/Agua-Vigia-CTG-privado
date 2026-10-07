// Planificación pura del guion: qué vecinos se registran en qué barrio y cuándo sale cada reporte. Sin red ni reloj, para poder probarla.

const TIPOS_DE_REPORTE = new Set(['SIN_AGUA', 'PRESION_BAJA', 'SERVICIO_RESTABLECIDO']);

/** Generador pseudoaleatorio con semilla (mulberry32): el mismo guion registra siempre a los mismos vecinos en los mismos barrios. */
function aleatorio(semilla) {
  let estado = semilla >>> 0;
  return () => {
    estado = (estado + 0x6d2b79f5) >>> 0;
    let t = estado;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Reparte `cantidad` vecinos entre los barrios. `clave` fija cuántos van a cada barrio que el guion necesita (los de los actos); el resto se
 * reparte al azar —con semilla— entre los demás. En los barrios clave verifica el barrio la fracción pedida (redondeada hacia arriba, los
 * primeros de cada barrio) para que los actos tengan siempre vecinos verificados; de los demás, `fuera` intentan verificarlo con una ubicación
 * de otro barrio y la API debe rechazarlos (422).
 */
export function repartirVecinos({ cantidad, clave, sectores, semilla = 1, fuera = 6, fraccionVerifican = 0.8 }) {
  for (const barrio of Object.keys(clave)) {
    if (!sectores.includes(barrio)) throw new Error(`El barrio clave '${barrio}' no existe entre los sectores de la instancia`);
  }
  const enClave = Object.values(clave).reduce((suma, n) => suma + n, 0);
  if (enClave + fuera > cantidad) {
    throw new Error(`No caben: los barrios clave piden ${enClave} vecinos y ${fuera} intentan desde fuera, y la cantidad es ${cantidad}`);
  }

  const azar = aleatorio(semilla);
  const resto = sectores.filter((s) => !(s in clave));
  const plan = [];
  for (const [barrio, n] of Object.entries(clave)) {
    const verifican = Math.ceil(n * fraccionVerifican);
    for (let i = 0; i < n; i++) plan.push({ barrio, verificar: i < verifican, intentoFuera: false });
  }
  for (let i = 0; i < cantidad - enClave; i++) {
    const barrio = resto[Math.floor(azar() * resto.length)];
    plan.push({ barrio, verificar: azar() < fraccionVerifican, intentoFuera: false });
  }
  // Los que intentan desde fuera salen de los vecinos «de relleno», de los últimos a los primeros.
  for (let i = 0; i < fuera; i++) {
    const vecino = plan[plan.length - 1 - i];
    vecino.verificar = false;
    vecino.intentoFuera = true;
  }
  return plan.map((v, indice) => ({ indice, ...v }));
}

/**
 * Desglosa una acción `reportes` en un reporte por identidad: primero los vecinos verificados, luego los anónimos con ubicación y luego los
 * anónimos sin ella. Con `repartir`, se escalonan entre el inicio y el final del acto (el cierre «primer reporte del grupo» depende de que no
 * lleguen todos en el mismo minuto); sin él, salen todos al empezar.
 */
export function planificarReportes(accion, acto) {
  const { barrio, etiqueta } = accion;
  const tipoReporte = accion['tipo-reporte'];
  if (!barrio) throw new Error("La acción 'reportes' necesita 'barrio'");
  if (!tipoReporte) throw new Error("La acción 'reportes' necesita 'tipo-reporte'");
  if (!TIPOS_DE_REPORTE.has(tipoReporte)) throw new Error(`El tipo de reporte '${tipoReporte}' no existe (${[...TIPOS_DE_REPORTE].join(', ')})`);
  if (!etiqueta) throw new Error("La acción 'reportes' necesita una 'etiqueta' para poder referirse a ellos después");
  const verificados = accion.verificados ?? 0;
  const anonimos = accion.anonimos ?? 0;
  const conUbicacion = accion['anonimos-con-ubicacion'] ?? 0;
  if (conUbicacion > anonimos) throw new Error(`No se puede pedir ${conUbicacion} anónimos con ubicación entre ${anonimos} anónimos`);

  const identidades = [
    ...Array.from({ length: verificados }, () => ({ identidad: 'vecino', conUbicacion: false })),
    ...Array.from({ length: conUbicacion }, () => ({ identidad: 'anonimo', conUbicacion: true })),
    ...Array.from({ length: anonimos - conUbicacion }, () => ({ identidad: 'anonimo', conUbicacion: false })),
  ];
  const total = identidades.length;
  return identidades.map((identidad, ordinal) => ({
    minuto: accion.repartir && total > 1 ? acto.minuto + (ordinal * acto.duracion) / (total - 1) : acto.minuto,
    accion: { tipo: 'reporte-individual', barrio, tipoReporte, etiqueta, ordinal, ...identidad },
  }));
}

/** «11:10» → minutos desde la hora de inicio del guion («08:00» → 190). */
export function minutoDeHora(hora, inicio) {
  const aMinutos = (hhmm) => {
    const [h, m] = hhmm.split(':').map(Number);
    return h * 60 + m;
  };
  return aMinutos(hora) - aMinutos(inicio);
}
