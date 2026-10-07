import { test } from 'node:test';
import assert from 'node:assert/strict';
import { exigirInstanciaDeSimulacion } from '../simulacion/lib/instancia.mjs';

const apiQueResponde = (cuerpo) => ({ base: 'http://x', get: async () => ({ cuerpo }) });
const apiCaida = { base: 'http://x', get: async () => { throw new Error('ECONNREFUSED'); } };

test('acepta una API que se declara de simulación', async () => {
  await assert.doesNotReject(exigirInstanciaDeSimulacion(apiQueResponde({ modo: 'SIMULACION' })));
});

test('se niega ante una API real y lo dice', async () => {
  await assert.rejects(exigirInstanciaDeSimulacion(apiQueResponde({ modo: 'REAL' })), /modo=REAL.*no es la instancia de simulación/);
});

test('se niega si no puede comprobarlo: sin saber con qué habla no borra nada', async () => {
  await assert.rejects(exigirInstanciaDeSimulacion(apiCaida), /No se pudo consultar/);
});
