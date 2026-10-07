import { test } from 'node:test';
import assert from 'node:assert/strict';
import { motivoParaNoContinuar } from '../lib/guarda-destructiva.mjs';

// Una base en memoria con solo lo que usa buscarDatosReales: countDocuments con igualdad y $ne.
function baseFalsa(colecciones) {
  const coincide = (doc, filtro) => Object.entries(filtro).every(([campo, condicion]) => {
    const valor = doc[campo];
    if (condicion !== null && typeof condicion === 'object') {
      return !('$ne' in condicion && valor === condicion.$ne);
    }
    return valor === condicion;
  });
  return {
    collection: (nombre) => ({
      countDocuments: async (filtro = {}) => (colecciones[nombre] ?? []).filter((d) => coincide(d, filtro)).length,
    }),
  };
}

const LOCAL = 'mongodb://localhost:27017/?directConnection=true';
const AJENA = 'mongodb://servidor-ajeno.example:27017/';
const baseVacia = () => baseFalsa({ sectores: [{}] });
const baseConReportes = () => baseFalsa({ reportes: [{}, {}] });

test('una base local sin datos reales deja continuar', async () => {
  assert.equal(await motivoParaNoContinuar({ uri: LOCAL, db: baseVacia(), accion: 'borrar los sectores' }), null);
});

test('una base que no es local se rechaza, y nombra la acción que se iba a hacer', async () => {
  const motivo = await motivoParaNoContinuar({ uri: AJENA, db: baseVacia(), accion: 'borrar los sectores' });
  assert.match(motivo, /borrar los sectores/);
  assert.match(motivo, /--permitir-remoto/);
});

test('con --permitir-remoto una base ajena sin datos reales deja continuar', async () => {
  assert.equal(await motivoParaNoContinuar({ uri: AJENA, db: baseVacia(), accion: 'x', permitirRemoto: true }), null);
});

test('una base local con reportes, cortes o propuestas se rechaza y dice cuántos hay', async () => {
  const motivo = await motivoParaNoContinuar({ uri: LOCAL, db: baseConReportes(), accion: 'borrar los sectores' });
  assert.match(motivo, /reportes: 2/);
  assert.match(motivo, /--sobre-datos-reales/);
});

test('con --sobre-datos-reales una base local con datos continúa', async () => {
  assert.equal(await motivoParaNoContinuar({
    uri: LOCAL, db: baseConReportes(), accion: 'x', sobreDatosReales: true }), null);
});

test('permitir lo remoto no basta para pisar datos reales: son dos permisos distintos', async () => {
  const motivo = await motivoParaNoContinuar({
    uri: AJENA, db: baseConReportes(), accion: 'x', permitirRemoto: true });
  assert.match(motivo, /--sobre-datos-reales/);
});

test('una base remota no consulta la base antes de rechazar', async () => {
  const db = { collection: () => { throw new Error('no debía consultarse'); } };
  assert.match(await motivoParaNoContinuar({ uri: AJENA, db, accion: 'x' }), /--permitir-remoto/);
});
