import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buscarDatosReales, mensajeDatosReales } from '../lib/datos-reales.mjs';

// Una base en memoria con solo lo que usa buscarDatosReales: countDocuments con igualdad, $ne y $nin.
function baseFalsa(colecciones) {
  const coincide = (doc, filtro) => Object.entries(filtro).every(([campo, condicion]) => {
    const valor = doc[campo];
    if (condicion !== null && typeof condicion === 'object') {
      if ('$ne' in condicion && valor === condicion.$ne) return false;
      if ('$nin' in condicion && condicion.$nin.includes(valor)) return false;
      return true;
    }
    return valor === condicion;
  });
  return {
    collection: (nombre) => ({
      countDocuments: async (filtro = {}) => (colecciones[nombre] ?? []).filter((d) => coincide(d, filtro)).length,
    }),
  };
}

const sintetica = { rol: 'VECINO', datosDeDemostracion: true, origen: 'SEMBRADO' };
const panelDeDemostracion = { rol: 'VEEDOR', datosDeDemostracion: true };
const adminInicial = { rol: 'ADMIN' };

test('una base recién sembrada (sectores, ADMIN y cuentas sintéticas) no tiene datos reales', async () => {
  const db = baseFalsa({ sectores: [{}], usuarios: [adminInicial, sintetica, sintetica, panelDeDemostracion] });
  assert.deepEqual(await buscarDatosReales(db), []);
});

test('un reporte, un corte o una propuesta de ingesta son datos reales', async () => {
  for (const coleccion of ['reportes', 'cortes', 'propuestas_ingesta']) {
    const hallazgos = await buscarDatosReales(baseFalsa({ [coleccion]: [{}, {}] }));
    assert.deepEqual(hallazgos.map((h) => [h.coleccion, h.cantidad]), [[coleccion, 2]]);
  }
});

test('una cuenta que no es sintética ni de demostración es real, salvo el ADMIN inicial', async () => {
  const vecinaReal = { rol: 'VECINO' };
  const hallazgos = await buscarDatosReales(baseFalsa({ usuarios: [adminInicial, sintetica, panelDeDemostracion, vecinaReal] }));
  assert.deepEqual(hallazgos.map((h) => [h.coleccion, h.cantidad]), [['usuarios', 1]]);
});

test('el mensaje dice qué se encontró y qué se arriesga, y cómo seguir a propósito', () => {
  const texto = mensajeDatosReales([{ coleccion: 'reportes', cantidad: 12 }, { coleccion: 'usuarios', cantidad: 3 }]);
  assert.match(texto, /reportes: 12/);
  assert.match(texto, /usuarios: 3/);
  assert.match(texto, /mongorestore --drop/);
  assert.match(texto, /perfil `carga`/);
  assert.match(texto, /--sobre-datos-reales/);
});
