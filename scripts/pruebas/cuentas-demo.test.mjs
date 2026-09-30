import { test } from 'node:test';
import assert from 'node:assert/strict';
import { crearFabricaDeCuentas } from '../lib/cuentas-demo.mjs';
import { crearFuenteFaker } from '../lib/fuente-faker.mjs';

const SECTORES = Array.from({ length: 20 }, (_, i) => ({ slug: `barrio-${i}`, poblacion: 1000 + i * 250 }));

function fabricaFaker(opciones = {}, extras = {}) {
  const fabrica = crearFabricaDeCuentas({ ...crearFuenteFaker(opciones), extras });
  fabrica.cargarBarrios(SECTORES);
  return fabrica;
}

test('dos ejecuciones sin semilla no repiten correos aunque se generen miles', () => {
  const primera = fabricaFaker();
  const correos = new Set(Array.from({ length: 3000 }, () => primera.crearCuenta().usuario.correo));
  const segunda = fabricaFaker();
  // Como en agregar-usuarios.mjs: los correos que ya existen en la base se cargan antes de generar.
  for (const correo of correos) segunda.correosUsados.add(correo);
  const nuevos = Array.from({ length: 3000 }, () => segunda.crearCuenta().usuario.correo);
  assert.equal(new Set(nuevos).size, 3000);
  assert.ok(nuevos.every((correo) => !correos.has(correo)));
});

test('sin semilla cada ejecución genera personas distintas', () => {
  const nombres = (fabrica) => Array.from({ length: 50 }, () => fabrica.crearCuenta().usuario.nombre).join('|');
  assert.notEqual(nombres(fabricaFaker()), nombres(fabricaFaker()));
});

test('con la misma semilla se reproduce la misma serie', () => {
  const serie = () => {
    const fabrica = fabricaFaker({ semilla: 7 });
    return Array.from({ length: 20 }, () => fabrica.crearCuenta().usuario.correo).join('|');
  };
  assert.equal(serie(), serie());
});

test('la marca del lote llega a la cuenta y a todo lo que deja en el sistema', () => {
  const fabrica = fabricaFaker({}, { lote: 'lote-prueba' });
  for (let i = 0; i < 300; i++) {
    const { usuario, tokens, auditoria, suscripciones } = fabrica.crearCuenta();
    for (const documento of [usuario, ...tokens, ...auditoria, ...suscripciones]) {
      assert.equal(documento.lote, 'lote-prueba');
      assert.equal(documento.datosDeDemostracion, true);
    }
  }
});

test('cada cuenta vive en un barrio conocido y nunca con fechas futuras', () => {
  const ahora = Date.parse('2026-09-29T12:00:00Z');
  const fabrica = crearFabricaDeCuentas({ ...crearFuenteFaker({ semilla: 1 }), ahora });
  fabrica.cargarBarrios(SECTORES);
  const slugs = new Set(SECTORES.map((s) => s.slug));
  for (let i = 0; i < 500; i++) {
    const { usuario, auditoria } = fabrica.crearCuenta();
    assert.ok(slugs.has(usuario.barrio));
    assert.ok(usuario.creadoEn.getTime() <= ahora && usuario.actualizadoEn.getTime() <= ahora);
    assert.ok(auditoria.every((e) => e.ocurrioEn.getTime() <= ahora));
  }
});
