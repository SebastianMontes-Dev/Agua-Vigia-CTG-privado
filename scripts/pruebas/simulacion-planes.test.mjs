import { test } from 'node:test';
import assert from 'node:assert/strict';
import { repartirVecinos, planificarReportes, minutoDeHora } from '../simulacion/lib/planes.mjs';

const SECTORES = Array.from({ length: 40 }, (_, i) => `barrio-${i}`).concat(['manga', 'nelson-mandela', 'san-fernando', 'albornoz']);
const CLAVE = { manga: 30, 'nelson-mandela': 45, 'san-fernando': 25, albornoz: 8 };

test('reparte exactamente la cantidad pedida y los barrios clave reciben lo que piden', () => {
  const plan = repartirVecinos({ cantidad: 300, clave: CLAVE, sectores: SECTORES, fuera: 6 });
  assert.equal(plan.length, 300);
  for (const [barrio, n] of Object.entries(CLAVE)) assert.equal(plan.filter((v) => v.barrio === barrio).length, n, barrio);
});

test('en los barrios clave verifican el 80 % redondeado hacia arriba, y ninguno intenta desde fuera', () => {
  const plan = repartirVecinos({ cantidad: 300, clave: CLAVE, sectores: SECTORES, fuera: 6 });
  for (const [barrio, n] of Object.entries(CLAVE)) {
    const suyos = plan.filter((v) => v.barrio === barrio);
    assert.equal(suyos.filter((v) => v.verificar).length, Math.ceil(n * 0.8), barrio);
    assert.equal(suyos.filter((v) => v.intentoFuera).length, 0, barrio);
  }
});

test('exactamente 6 intentan verificar con una ubicación de otro barrio y esos no verifican', () => {
  const plan = repartirVecinos({ cantidad: 300, clave: CLAVE, sectores: SECTORES, fuera: 6 });
  const fuera = plan.filter((v) => v.intentoFuera);
  assert.equal(fuera.length, 6);
  assert.ok(fuera.every((v) => v.verificar === false));
  assert.ok(fuera.every((v) => !(v.barrio in CLAVE)));
});

test('es determinista con la misma semilla y distinto con otra', () => {
  const a = repartirVecinos({ cantidad: 120, clave: CLAVE, sectores: SECTORES, semilla: 5 });
  const b = repartirVecinos({ cantidad: 120, clave: CLAVE, sectores: SECTORES, semilla: 5 });
  const c = repartirVecinos({ cantidad: 120, clave: CLAVE, sectores: SECTORES, semilla: 6 });
  assert.deepEqual(a, b);
  assert.notDeepEqual(a, c);
});

test('cada vecino tiene un índice único y estable', () => {
  const plan = repartirVecinos({ cantidad: 50, clave: { manga: 10 }, sectores: SECTORES });
  assert.deepEqual(plan.map((v) => v.indice), Array.from({ length: 50 }, (_, i) => i));
});

test('se queja si los barrios clave y los de fuera no caben en la cantidad', () => {
  assert.throws(() => repartirVecinos({ cantidad: 100, clave: CLAVE, sectores: SECTORES, fuera: 6 }), /caben|cantidad/);
});

test('se queja si un barrio clave no existe', () => {
  assert.throws(() => repartirVecinos({ cantidad: 300, clave: { inventado: 5 }, sectores: SECTORES }), /inventado/);
});

// --- reportes -------------------------------------------------------------------------------------------------------------------

const ACTO = { minuto: 490, duracion: 15 };
const BASE = { tipo: 'reportes', barrio: 'nelson-mandela', 'tipo-reporte': 'SERVICIO_RESTABLECIDO', etiqueta: 'restablecen', verificados: 5, anonimos: 3, 'anonimos-con-ubicacion': 1 };

test('planifica un reporte por identidad: verificados, luego anónimos con ubicación, luego sin', () => {
  const unidades = planificarReportes(BASE, ACTO);
  assert.equal(unidades.length, 8);
  const quienes = unidades.map((u) => `${u.accion.identidad}${u.accion.conUbicacion ? '+u' : ''}`);
  assert.deepEqual(quienes, ['vecino', 'vecino', 'vecino', 'vecino', 'vecino', 'anonimo+u', 'anonimo', 'anonimo']);
  assert.ok(unidades.every((u) => u.accion.tipo === 'reporte-individual' && u.accion.barrio === 'nelson-mandela' && u.accion.etiqueta === 'restablecen'));
  assert.deepEqual(unidades.map((u) => u.accion.ordinal), [0, 1, 2, 3, 4, 5, 6, 7]);
});

test('sin repartir salen todos en el minuto del acto', () => {
  const unidades = planificarReportes(BASE, ACTO);
  assert.ok(unidades.every((u) => u.minuto === 490));
});

test('repartidos se escalonan hasta el final del acto', () => {
  const unidades = planificarReportes({ ...BASE, repartir: true }, ACTO);
  assert.equal(unidades[0].minuto, 490);
  assert.equal(unidades.at(-1).minuto, 505);
  for (let i = 1; i < unidades.length; i++) assert.ok(unidades[i].minuto >= unidades[i - 1].minuto);
});

test('un solo reporte repartido sale al principio', () => {
  const unidades = planificarReportes({ ...BASE, verificados: 1, anonimos: 0, 'anonimos-con-ubicacion': 0, repartir: true }, ACTO);
  assert.deepEqual(unidades.map((u) => u.minuto), [490]);
});

test('no se pueden pedir más anónimos con ubicación que anónimos', () => {
  assert.throws(() => planificarReportes({ ...BASE, 'anonimos-con-ubicacion': 4 }, ACTO), /ubicaci/);
});

test('hace falta barrio, tipo de reporte y etiqueta', () => {
  assert.throws(() => planificarReportes({ ...BASE, barrio: undefined }, ACTO), /barrio/);
  assert.throws(() => planificarReportes({ ...BASE, 'tipo-reporte': undefined }, ACTO), /tipo-reporte/);
  assert.throws(() => planificarReportes({ ...BASE, etiqueta: undefined }, ACTO), /etiqueta/);
});

test('el tipo de reporte tiene que ser uno de los tres que acepta la API', () => {
  assert.throws(() => planificarReportes({ ...BASE, 'tipo-reporte': 'INVENTADO' }, ACTO), /INVENTADO/);
});

// --- horas -----------------------------------------------------------------------------------------------------------------------

test('una hora del día en minutos desde el inicio del guion', () => {
  assert.equal(minutoDeHora('08:00', '08:00'), 0);
  assert.equal(minutoDeHora('11:10', '08:00'), 190);
  assert.equal(minutoDeHora('16:05', '08:00'), 485);
});
