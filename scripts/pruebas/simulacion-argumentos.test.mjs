import { test } from 'node:test';
import assert from 'node:assert/strict';
import { analizarArgumentos } from '../simulacion/lib/argumentos.mjs';

test('sin argumentos muestra la ayuda', () => {
  assert.deepEqual(analizarArgumentos([]), { comando: 'ayuda' });
});

test('iniciar acepta velocidad, guion y banderas, en cualquier orden', () => {
  assert.deepEqual(analizarArgumentos(['iniciar', '--velocidad', '300', '--reiniciar', '--guion', 'otro.yaml', '--detener-en-fallo']), {
    comando: 'iniciar',
    velocidad: 300,
    guion: 'otro.yaml',
    reiniciar: true,
    detenerEnFallo: true,
  });
});

test('iniciar sin opciones deja todo por defecto', () => {
  assert.deepEqual(analizarArgumentos(['iniciar']), { comando: 'iniciar', velocidad: undefined, guion: undefined, reiniciar: false, detenerEnFallo: false });
});

test('la velocidad admite el prefijo x y se rechazan valores no positivos', () => {
  assert.equal(analizarArgumentos(['velocidad', 'x10']).velocidad, 10);
  assert.equal(analizarArgumentos(['velocidad', '10']).velocidad, 10);
  assert.throws(() => analizarArgumentos(['velocidad', '0']), /mayor que 0/);
  assert.throws(() => analizarArgumentos(['velocidad', 'rapido']), /mayor que 0/);
  assert.throws(() => analizarArgumentos(['velocidad']), /velocidad/);
  assert.throws(() => analizarArgumentos(['iniciar', '--velocidad', '-5']), /mayor que 0/);
});

test('saltar pide horas positivas', () => {
  assert.deepEqual(analizarArgumentos(['saltar', '7']), { comando: 'saltar', horas: 7 });
  assert.deepEqual(analizarArgumentos(['saltar', '1,5']), { comando: 'saltar', horas: 1.5 });
  assert.throws(() => analizarArgumentos(['saltar']), /horas/);
  assert.throws(() => analizarArgumentos(['saltar', '-2']), /mayor que 0/);
});

test('pausar, reanudar, estado y reiniciar no llevan argumentos', () => {
  for (const c of ['pausar', 'reanudar', 'estado', 'reiniciar']) assert.deepEqual(analizarArgumentos([c]), { comando: c });
});

test('un comando o una opción desconocidos fallan diciendo cuáles hay', () => {
  assert.throws(() => analizarArgumentos(['volar']), /comando 'volar'.*iniciar/s);
  assert.throws(() => analizarArgumentos(['iniciar', '--turbo']), /opción '--turbo'/);
  assert.throws(() => analizarArgumentos(['iniciar', '--velocidad']), /--velocidad.*valor/);
});
