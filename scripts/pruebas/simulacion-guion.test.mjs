import { test } from 'node:test';
import assert from 'node:assert/strict';
import { leerGuion, instanteDeMinuto, hora12 } from '../simulacion/lib/guion.mjs';

const MINIMO = `
version: 1
nombre: Prueba
velocidad: 60
dia: "2026-11-02"
hora: "08:00"
actos:
  - id: uno
    minuto: 0
    titulo: Primero
    acciones:
      - tipo: preparar-panel
    aserciones:
      - tipo: modo
        valor: SIMULACION
  - id: dos
    minuto: 20
    duracion: 15
    acciones:
      - tipo: reportes
        barrio: manga
        tipo-reporte: SIN_AGUA
        cantidad: 3
        repartir: true
`;

test('lee un guion válido y normaliza los actos', () => {
  const guion = leerGuion(MINIMO);
  assert.equal(guion.nombre, 'Prueba');
  assert.equal(guion.velocidad, 60);
  assert.equal(guion.actos.length, 2);
  assert.equal(guion.actos[0].duracion, 0);
  assert.equal(guion.actos[1].duracion, 15);
  assert.equal(guion.actos[1].titulo, 'dos');
  assert.deepEqual(guion.actos[0].aserciones, [{ tipo: 'modo', valor: 'SIMULACION' }]);
});

test('rechaza una versión desconocida', () => {
  assert.throws(() => leerGuion(MINIMO.replace('version: 1', 'version: 2')), /versión/);
});

test('rechaza actos con el mismo id', () => {
  assert.throws(() => leerGuion(MINIMO.replace('id: dos', 'id: uno')), /repetido/);
});

test('rechaza actos que no van en orden cronológico', () => {
  assert.throws(() => leerGuion(MINIMO.replace('minuto: 20', 'minuto: -5')), /orden|minuto/);
});

test('rechaza un acto que empieza antes de que termine el anterior', () => {
  assert.throws(() => leerGuion(MINIMO.replace('minuto: 0', 'minuto: 30')), /orden|minuto|antes/);
});

test('rechaza una acción de un tipo que no existe y dice en qué acto', () => {
  assert.throws(() => leerGuion(MINIMO.replace('preparar-panel', 'inventada')), /inventada.*uno|uno.*inventada/s);
});

test('rechaza una aserción de un tipo que no existe', () => {
  assert.throws(() => leerGuion(MINIMO.replace('tipo: modo', 'tipo: no-existe')), /no-existe/);
});

test('un guion sin actos no vale', () => {
  assert.throws(() => leerGuion('version: 1\nnombre: x\nactos: []'), /actos/);
});

test('el minuto 0 es la hora de inicio en Cartagena (UTC-5)', () => {
  assert.equal(instanteDeMinuto('2026-11-02', '08:00', 0).toISOString(), '2026-11-02T13:00:00.000Z');
  assert.equal(instanteDeMinuto('2026-11-02', '08:00', 150).toISOString(), '2026-11-02T15:30:00.000Z');
});

test('pasa a las 12 horas como lo escribe Acuacar', () => {
  assert.equal(hora12('10:00'), '10:00 a. m.');
  assert.equal(hora12('16:00'), '4:00 p. m.');
  assert.equal(hora12('12:30'), '12:30 p. m.');
  assert.equal(hora12('00:15'), '12:15 a. m.');
});
