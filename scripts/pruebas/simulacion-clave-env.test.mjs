import { test } from 'node:test';
import assert from 'node:assert/strict';
import { asegurarClaveEnEnv } from '../simulacion/lib/clave-env.mjs';

const CLAVE = 'a'.repeat(48);
const generar = () => CLAVE;

test('un .env vacío o inexistente recibe la clave', () => {
  assert.deepEqual(asegurarClaveEnEnv('', generar), { contenido: `SIMULACION_CLAVE=${CLAVE}\n`, estado: 'creada' });
  assert.deepEqual(asegurarClaveEnEnv(null, generar), { contenido: `SIMULACION_CLAVE=${CLAVE}\n`, estado: 'creada' });
});

test('se añade al final sin tocar el resto, con un salto de línea intermedio si faltaba', () => {
  const r = asegurarClaveEnEnv('JWT_SECRET=abc', generar);
  assert.equal(r.contenido, `JWT_SECRET=abc\nSIMULACION_CLAVE=${CLAVE}\n`);
  assert.equal(r.estado, 'creada');
});

test('una línea con la clave vacía se rellena en su sitio', () => {
  const r = asegurarClaveEnEnv('A=1\nSIMULACION_CLAVE=\nB=2\n', generar);
  assert.equal(r.contenido, `A=1\nSIMULACION_CLAVE=${CLAVE}\nB=2\n`);
  assert.equal(r.estado, 'creada');
});

test('NUNCA sobrescribe una clave que ya existe', () => {
  const existente = `A=1\nSIMULACION_CLAVE=${'b'.repeat(40)}\n`;
  const r = asegurarClaveEnEnv(existente, generar);
  assert.equal(r.contenido, existente);
  assert.equal(r.estado, 'existente');
});

test('una clave existente demasiado corta se deja tal cual pero se avisa', () => {
  const existente = 'SIMULACION_CLAVE=corta\n';
  const r = asegurarClaveEnEnv(existente, generar);
  assert.equal(r.contenido, existente);
  assert.equal(r.estado, 'corta');
});

test('conserva los finales de línea CRLF del archivo', () => {
  const r = asegurarClaveEnEnv('A=1\r\nB=2\r\n', generar);
  assert.equal(r.contenido, `A=1\r\nB=2\r\nSIMULACION_CLAVE=${CLAVE}\r\n`);
});

test('una línea comentada no cuenta como clave definida', () => {
  const r = asegurarClaveEnEnv('# SIMULACION_CLAVE=xxxxxxxx\n', generar);
  assert.match(r.contenido, new RegExp(`\\nSIMULACION_CLAVE=${CLAVE}\\n$`));
  assert.equal(r.estado, 'creada');
});

test('el resultado nunca devuelve la clave en el estado (para no imprimirla por descuido)', () => {
  const r = asegurarClaveEnEnv('', generar);
  assert.deepEqual(Object.keys(r).sort(), ['contenido', 'estado']);
});
