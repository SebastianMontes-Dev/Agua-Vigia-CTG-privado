import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { leerGuion } from '../simulacion/lib/guion.mjs';
import { construirLineaDeTiempo } from '../simulacion/lib/linea-de-tiempo.mjs';

const guion = leerGuion(readFileSync(new URL('../simulacion/guion-completo.yaml', import.meta.url), 'utf8'));
const acciones = guion.actos.flatMap((a) => a.acciones);

test('el guion completo es válido y empieza a las 08:00 a velocidad x60', () => {
  assert.equal(guion.hora, '08:00');
  assert.equal(guion.velocidad, 60);
  assert.ok(guion.actos.length >= 15);
});

test('no menciona sensores: el IoT está construido pero inactivo', () => {
  const texto = readFileSync(new URL('../simulacion/guion-completo.yaml', import.meta.url), 'utf8').toLowerCase();
  assert.doesNotMatch(texto, /sensor|iot|pozon|pozón/);
});

test('cada barrio clave tiene vecinos verificados de sobra para los reportes que el guion les pide', () => {
  const registro = acciones.find((a) => a.tipo === 'registrar-vecinos');
  const fraccion = registro['fraccion-verifican'] ?? 0.8;
  const pedidos = new Map();
  for (const a of acciones.filter((x) => x.tipo === 'reportes')) pedidos.set(a.barrio, (pedidos.get(a.barrio) ?? 0) + (a.verificados ?? 0));
  for (const [barrio, necesitan] of pedidos) {
    if (necesitan === 0) continue;
    const registrados = registro['barrios-clave'][barrio];
    assert.ok(registrados !== undefined, `${barrio} pide vecinos verificados pero no está en barrios-clave`);
    assert.ok(Math.ceil(registrados * fraccion) >= necesitan, `${barrio}: ${necesitan} reportes de vecino y solo ${Math.ceil(registrados * fraccion)} verificados`);
  }
});

test('los reportes de un barrio llevan etiquetas distintas y las etiquetas que se citan después existen antes', () => {
  const vistas = new Set();
  for (const acto of guion.actos) {
    for (const a of acto.acciones) {
      if (a.tipo === 'reportes') {
        assert.ok(!vistas.has(a.etiqueta), `etiqueta repetida: ${a.etiqueta}`);
        vistas.add(a.etiqueta);
      }
      const cita = a.tipo === 'foto' ? a.reporte : a.tipo === 'veedor-reportes' || a.tipo === 'veedor-foto' ? a.etiqueta : null;
      if (cita) assert.ok(vistas.has(cita), `el acto '${acto.id}' cita la etiqueta '${cita}' antes de crearla`);
    }
    for (const s of acto.aserciones) {
      if (s.tipo === 'foto-publica') assert.ok(vistas.has(s.etiqueta), `el acto '${acto.id}' asierta la foto de '${s.etiqueta}', que no existe`);
    }
  }
});

test('los boletines que el veedor descarta se enviaron antes', () => {
  const enviados = new Set();
  for (const acto of guion.actos) {
    for (const a of acto.acciones) {
      if (a.tipo === 'boletin') enviados.add(a.id);
      if (a.tipo === 'veedor-descarta-propuesta') assert.ok(enviados.has(a.boletin), `se descarta el boletín ${a.boletin} antes de enviarlo`);
    }
  }
});

test('la línea de tiempo se construye y cada acto termina con sus aserciones', () => {
  const linea = construirLineaDeTiempo(guion);
  assert.equal(linea.filter((e) => e.tipo === 'fin-de-acto').length, guion.actos.length);
  const minutos = linea.map((e) => e.minuto);
  assert.deepEqual(minutos, [...minutos].sort((a, b) => a - b));
});

test('la salida del guion aserta lo que el plan promete (§12)', () => {
  const aserciones = guion.actos.flatMap((a) => a.aserciones);
  const tipos = new Set(aserciones.map((a) => a.tipo));
  for (const esperado of ['modo', 'todos-sin-datos', 'vecinos-registrados', 'estado-sector', 'bitacora-contiene', 'disputas', 'corte', 'cortes-vencidos', 'senal-de-red', 'foto-publica', 'cumplimiento-sector', 'calidad-del-cumplimiento', 'indice-excluye-anulados', 'avisos-por-correo', 'sector-sin-verificacion-reciente']) {
    assert.ok(tipos.has(esperado), `el guion no usa la aserción '${esperado}'`);
  }
});
