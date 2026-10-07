import { test } from 'node:test';
import assert from 'node:assert/strict';
import { construirLineaDeTiempo } from '../simulacion/lib/linea-de-tiempo.mjs';

const resumen = (linea) =>
  linea.map((e) => `${e.minuto}:${e.tipo}:${e.acto}${e.tipo === 'accion' ? ':' + e.accion.tipo : ''}`);

const GUION = {
  actos: [
    {
      id: 'a',
      minuto: 0,
      duracion: 0,
      acciones: [{ tipo: 'preparar-panel' }, { tipo: 'boletin', id: 'A', plantilla: 'corte' }],
      aserciones: [{ tipo: 'modo', valor: 'SIMULACION' }],
    },
    {
      id: 'b',
      minuto: 20,
      duracion: 15,
      acciones: [
        { tipo: 'reportes', barrio: 'manga', 'tipo-reporte': 'SIN_AGUA', etiqueta: 'r', verificados: 2, anonimos: 1, repartir: true },
      ],
      aserciones: [],
    },
    { id: 'c', minuto: 100, duracion: 0, acciones: [], aserciones: [{ tipo: 'todos-sin-datos' }] },
  ],
};

test('ordena acciones y fines de acto, expande los reportes y escalona los repartidos', () => {
  assert.deepEqual(resumen(construirLineaDeTiempo(GUION)), [
    '0:accion:a:preparar-panel',
    '0:accion:a:boletin',
    '0:fin-de-acto:a',
    '20:accion:b:reporte-individual',
    '27.5:accion:b:reporte-individual',
    '35:accion:b:reporte-individual',
    '35:fin-de-acto:b',
    '100:fin-de-acto:c',
  ]);
});

test('el fin de acto lleva las aserciones del acto, también cuando no tiene acciones', () => {
  const linea = construirLineaDeTiempo(GUION);
  const fin = linea.find((e) => e.tipo === 'fin-de-acto' && e.acto === 'c');
  assert.deepEqual(fin.aserciones, [{ tipo: 'todos-sin-datos' }]);
});

test('un acto vacío produce solo su fin de acto', () => {
  const linea = construirLineaDeTiempo({ actos: [{ id: 'v', minuto: 5, duracion: 2, acciones: [], aserciones: [] }] });
  assert.deepEqual(resumen(linea), ['7:fin-de-acto:v']);
});

test('con una acción que no es reportes, la acción pasa tal cual y sin escalonar', () => {
  const accion = { tipo: 'saltar', horas: 7 };
  const linea = construirLineaDeTiempo({ actos: [{ id: 's', minuto: 10, duracion: 0, acciones: [accion], aserciones: [] }] });
  assert.equal(linea[0].accion, accion);
});

test('es estable: dos elementos en el mismo minuto conservan el orden del guion', () => {
  const linea = construirLineaDeTiempo({
    actos: [
      { id: 'x', minuto: 10, duracion: 0, acciones: [{ tipo: 'saltar', horas: 1 }, { tipo: 'saltar', horas: 2 }], aserciones: [] },
      { id: 'y', minuto: 10, duracion: 0, acciones: [{ tipo: 'saltar', horas: 3 }], aserciones: [] },
    ],
  });
  assert.deepEqual(
    linea.filter((e) => e.tipo === 'accion').map((e) => e.accion.horas),
    [1, 2, 3],
  );
});
