import { test } from 'node:test';
import assert from 'node:assert/strict';
import { boletinDeCorte, boletinDeBajaConfianza, boletinDeAplazamiento, fechaLarga } from '../simulacion/lib/plantilla-boletin.mjs';

const BASE = { numero: 7, dia: '2026-11-02', publicadoEn: '2026-11-02T08:20:00' };

test('la fecha larga está en español', () => {
  assert.equal(fechaLarga('2026-11-02'), 'lunes 2 de noviembre');
  assert.equal(fechaLarga('2026-12-25'), 'viernes 25 de diciembre');
});

test('un boletín de corte lleva la forma de la API de WordPress de Acuacar', () => {
  const b = boletinDeCorte({ ...BASE, desde: '10:00', hasta: '16:00', barrios: ['Manga', 'Nelson Mandela', 'San Fernando'] });
  assert.deepEqual(Object.keys(b).sort(), ['contenido', 'enlace', 'fecha', 'id', 'portada', 'titulo']);
  assert.equal(b.id, 7);
  assert.equal(b.fecha, '2026-11-02T08:20:00');
  assert.equal(b.enlace, 'https://simulacion.local/boletin/7');
  assert.match(b.titulo, /^#\d+ (–|&#8211;) \[SIMULACIÓN\] /);
  assert.match(b.contenido, /^<div/);
});

test('el contenido dice día, ventana en 12 horas y enumera los barrios, como un boletín real', () => {
  const { contenido } = boletinDeCorte({ ...BASE, desde: '10:00', hasta: '16:00', barrios: ['Manga', 'Nelson Mandela'] });
  assert.match(contenido, /este lunes 2 de noviembre, entre las 10:00 a\. m\. y las 4:00 p\. m\./);
  assert.match(contenido, /suspensión del servicio de acueducto en los siguientes barrios y sectores/);
  assert.match(contenido, /Manga, Nelson Mandela\./);
});

test('un corte para mañana habla de mañana', () => {
  const { contenido } = boletinDeCorte({ ...BASE, dia: '2026-11-03', desde: '09:00', hasta: '15:00', barrios: ['Crespo'] });
  assert.match(contenido, /este martes 3 de noviembre, entre las 9:00 a\. m\. y las 3:00 p\. m\./);
});

test('el boletín declara que es simulado y no se confunde con uno real', () => {
  const b = boletinDeCorte({ ...BASE, desde: '10:00', hasta: '16:00', barrios: ['Manga'] });
  assert.match(b.titulo, /SIMULACIÓN/);
  assert.match(b.enlace, /simulacion\.local/);
  assert.match(b.contenido, /SIMULACIÓN/);
});

test('el de baja confianza menciona un barrio suelto, sin ventana ni enumeración', () => {
  const b = boletinDeBajaConfianza({ ...BASE, barrio: 'Manga' });
  assert.match(b.contenido, /Manga/);
  assert.doesNotMatch(b.contenido, /entre las/);
  assert.doesNotMatch(b.contenido, /siguientes barrios/);
});

test('el aplazamiento nombra el barrio y dice que se aplaza', () => {
  const b = boletinDeAplazamiento({ ...BASE, barrio: 'Crespo', dia: '2026-11-03' });
  assert.match(b.contenido, /se aplaza/i);
  assert.match(b.contenido, /Crespo/);
  assert.match(b.contenido, /martes 3 de noviembre/);
});

test('un corte sin barrios no tiene sentido', () => {
  assert.throws(() => boletinDeCorte({ ...BASE, desde: '10:00', hasta: '16:00', barrios: [] }), /barrio/);
});

test('el contenido escapa lo que pondría una etiqueta HTML', () => {
  const b = boletinDeCorte({ ...BASE, desde: '10:00', hasta: '16:00', barrios: ['A<b>B'] });
  assert.doesNotMatch(b.contenido, /A<b>B/);
  assert.match(b.contenido, /A&lt;b&gt;B/);
});
