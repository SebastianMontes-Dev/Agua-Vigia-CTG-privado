import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Cronometro } from '../simulacion/lib/cronometro.mjs';

test('a x60 un segundo real es un minuto simulado', () => {
  const c = new Cronometro({ velocidad: 60 });
  c.avanzar(1000);
  assert.equal(c.minuto, 1);
  c.avanzar(2500);
  assert.equal(c.minuto, 3.5);
});

test('pausado no avanza, y reanudar sigue donde se quedó', () => {
  const c = new Cronometro({ velocidad: 60 });
  c.avanzar(1000);
  c.pausar();
  c.avanzar(5000);
  assert.equal(c.minuto, 1);
  c.reanudar();
  c.avanzar(1000);
  assert.equal(c.minuto, 2);
});

test('cambiar la velocidad solo afecta a lo que viene', () => {
  const c = new Cronometro({ velocidad: 60 });
  c.avanzar(1000);
  c.fijarVelocidad(300);
  c.avanzar(1000);
  assert.equal(c.minuto, 6);
});

test('x1 es tiempo real: un minuto real es un minuto simulado', () => {
  const c = new Cronometro({ velocidad: 1 });
  c.avanzar(60_000);
  assert.equal(c.minuto, 1);
});

test('saltar adelanta horas de golpe', () => {
  const c = new Cronometro({ velocidad: 60 });
  c.saltar(72 * 60);
  assert.equal(c.minuto, 4320);
});

test('congelado no avanza aunque no esté pausado (mientras se comprueban las aserciones)', () => {
  const c = new Cronometro({ velocidad: 60 });
  c.congelar();
  c.avanzar(10_000);
  assert.equal(c.minuto, 0);
  c.descongelar();
  c.avanzar(1000);
  assert.equal(c.minuto, 1);
});

test('pausa y congelación son independientes', () => {
  const c = new Cronometro({ velocidad: 60 });
  c.pausar();
  c.congelar();
  c.descongelar();
  c.avanzar(1000);
  assert.equal(c.minuto, 0, 'sigue pausado');
});

test('rechaza una velocidad que no sea positiva', () => {
  assert.throws(() => new Cronometro({ velocidad: 0 }), /velocidad/);
  const c = new Cronometro({ velocidad: 60 });
  assert.throws(() => c.fijarVelocidad(-5), /velocidad/);
  assert.throws(() => c.fijarVelocidad(Number.NaN), /velocidad/);
});

test('saltar rechaza lo que no sea un número positivo de minutos', () => {
  const c = new Cronometro({ velocidad: 60 });
  assert.throws(() => c.saltar(0), /minutos/);
  assert.throws(() => c.saltar(-10), /minutos/);
});

test('el estado se puede leer para mostrarlo', () => {
  const c = new Cronometro({ velocidad: 60 });
  c.pausar();
  assert.deepEqual(c.estado(), { minuto: 0, velocidad: 60, pausado: true });
});
