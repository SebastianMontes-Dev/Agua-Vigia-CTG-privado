// Lee y valida el guion declarativo de la simulación (scripts/simulacion/guion-completo.yaml). El guion dice QUÉ pasa y CUÁNDO (en minutos
// simulados desde la hora de inicio); cómo se hace cada cosa por la API lo decide acciones.mjs, y qué se espera lo decide aserciones.mjs.

import { parse } from 'yaml';

/** Las acciones que el ejecutor sabe hacer. Cada una la implementa acciones.mjs. */
export const ACCIONES = new Set([
  'preparar-panel',
  'registrar-vecinos',
  'suscribir-correos',
  'boletin',
  'veedor-descarta-propuesta',
  'reportes',
  'foto',
  'veedor-reportes',
  'veedor-foto',
  'veedor-cierra-barrio',
  'veedor-confirma-cierre',
  'veedor-anula-corte',
  'saltar',
]);

/** Lo que el ejecutor sabe comprobar contra la API (o la base de la simulación). Cada una la implementa aserciones.mjs. */
export const ASERCIONES = new Set([
  'modo',
  'todos-sin-datos',
  'vecinos-registrados',
  'estado-sector',
  'sector-sin-verificacion-reciente',
  'bitacora-contiene',
  'propuestas-pendientes',
  'disputas',
  'corte',
  'cortes-vencidos',
  'senal-de-red',
  'foto-publica',
  'cumplimiento-sector',
  'calidad-del-cumplimiento',
  'indice-excluye-anulados',
  'avisos-por-correo',
]);

const ZONA_CARTAGENA_MS = -5 * 60 * 60 * 1000;

/** El instante simulado del minuto `minuto` del día `dia` (AAAA-MM-DD) que empieza a la `hora` (HH:MM), hora de Cartagena (UTC-5, sin horario de verano). */
export function instanteDeMinuto(dia, hora, minuto) {
  const [anio, mes, d] = dia.split('-').map(Number);
  const [h, m] = hora.split(':').map(Number);
  const utc = Date.UTC(anio, mes - 1, d, h, m) - ZONA_CARTAGENA_MS + minuto * 60_000;
  return new Date(utc);
}

/** «16:00» → «4:00 p. m.», como lo escribe Acuacar en sus boletines. */
export function hora12(hhmm) {
  const [h, m] = hhmm.split(':').map(Number);
  const sufijo = h < 12 ? 'a. m.' : 'p. m.';
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12}:${String(m).padStart(2, '0')} ${sufijo}`;
}

function fallar(mensaje) {
  throw new Error(`Guion inválido: ${mensaje}`);
}

function validarLista(acto, lista, conocidos, campo, singular) {
  for (const elemento of lista) {
    if (elemento === null || typeof elemento !== 'object' || typeof elemento.tipo !== 'string') {
      fallar(`en el acto '${acto}', cada ${singular} necesita un 'tipo'`);
    }
    if (!conocidos.has(elemento.tipo)) {
      fallar(`el acto '${acto}' pide ${singular} '${elemento.tipo}', que no existe (${campo}: ${[...conocidos].join(', ')})`);
    }
  }
}

export function leerGuion(texto) {
  const crudo = parse(texto);
  if (crudo === null || typeof crudo !== 'object') fallar('el archivo está vacío');
  if (crudo.version !== 1) fallar(`versión ${crudo.version} no soportada (se esperaba 1)`);
  if (!Array.isArray(crudo.actos) || crudo.actos.length === 0) fallar("falta la lista de 'actos'");

  const vistos = new Set();
  let ultimoFin = -Infinity;
  const actos = crudo.actos.map((acto) => {
    if (typeof acto.id !== 'string' || acto.id === '') fallar("cada acto necesita un 'id'");
    if (vistos.has(acto.id)) fallar(`el id '${acto.id}' está repetido`);
    vistos.add(acto.id);
    if (typeof acto.minuto !== 'number' || acto.minuto < 0) fallar(`el acto '${acto.id}' necesita un 'minuto' mayor o igual que 0`);
    const duracion = acto.duracion ?? 0;
    if (typeof duracion !== 'number' || duracion < 0) fallar(`la 'duracion' del acto '${acto.id}' no es válida`);
    if (acto.minuto < ultimoFin) {
      fallar(`el acto '${acto.id}' (minuto ${acto.minuto}) va fuera de orden: empieza antes de que termine el anterior (minuto ${ultimoFin})`);
    }
    ultimoFin = acto.minuto + duracion;

    const acciones = acto.acciones ?? [];
    const aserciones = acto.aserciones ?? [];
    validarLista(acto.id, acciones, ACCIONES, 'acciones', 'acción');
    validarLista(acto.id, aserciones, ASERCIONES, 'aserciones', 'aserción');
    return { id: acto.id, titulo: acto.titulo ?? acto.id, minuto: acto.minuto, duracion, acciones, aserciones };
  });

  return {
    nombre: crudo.nombre ?? 'Guion',
    velocidad: crudo.velocidad ?? 60,
    dia: crudo.dia ?? null,
    hora: crudo.hora ?? '08:00',
    actos,
  };
}
