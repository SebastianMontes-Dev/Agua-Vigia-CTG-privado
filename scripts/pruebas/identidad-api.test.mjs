import { test } from 'node:test';
import assert from 'node:assert/strict';
import { puntoDentro, conDispositivo } from '../lib/identidad-api.mjs';

const cuadrado = { type: 'Polygon', coordinates: [[[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]]] };

function estaDentro([x, y], [exterior, ...huecos]) {
  const en = (anillo) => {
    let dentro = false;
    for (let i = 0, j = anillo.length - 1; i < anillo.length; j = i++) {
      const [xi, yi] = anillo[i];
      const [xj, yj] = anillo[j];
      if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) dentro = !dentro;
    }
    return dentro;
  };
  return en(exterior) && !huecos.some(en);
}

test('en un cuadrado devuelve el centro', () => {
  assert.deepEqual(puntoDentro(cuadrado), { latitud: 5, longitud: 5 });
});

test('en un polígono cóncavo (una C) no devuelve el centro de la caja, que cae en el hueco', () => {
  const c = { type: 'Polygon', coordinates: [[[0, 0], [10, 0], [10, 3], [3, 3], [3, 7], [10, 7], [10, 10], [0, 10], [0, 0]]] };
  const { latitud, longitud } = puntoDentro(c);
  assert.ok(estaDentro([longitud, latitud], c.coordinates));
  assert.notDeepEqual([longitud, latitud], [5, 5]);
});

test('respeta los huecos de un polígono', () => {
  const conHueco = { type: 'Polygon', coordinates: [cuadrado.coordinates[0], [[4, 4], [6, 4], [6, 6], [4, 6], [4, 4]]] };
  const { latitud, longitud } = puntoDentro(conHueco);
  assert.ok(estaDentro([longitud, latitud], conHueco.coordinates));
});

test('en un MultiPolygon devuelve un punto de alguna de sus partes', () => {
  const multi = {
    type: 'MultiPolygon',
    coordinates: [cuadrado.coordinates, [[[20, 20], [30, 20], [30, 30], [20, 30], [20, 20]]]],
  };
  const { latitud, longitud } = puntoDentro(multi);
  assert.ok(multi.coordinates.some((p) => estaDentro([longitud, latitud], p)));
});

test('las cabeceras de un reporte llevan el token de dispositivo y el tipo de contenido', () => {
  assert.deepEqual(conDispositivo('abc'), { 'Content-Type': 'application/json', 'X-Dispositivo': 'abc' });
});
