import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decodificarQuotedPrintable, extraerToken, tokensPorDestinatario, idsDeCorreosPara } from '../simulacion/lib/mailhog.mjs';

// Un correo como lo guarda Mailhog: el cuerpo viaja en quoted-printable (el '=' de la URL sale como '=3D' y las líneas largas se parten con '=').
const CUERPO = [
  '<html><body><p>Elige tu clave.</p>',
  '<a href=3D"http://localhost:8082/api/cuentas/enlaces/invitacion?token=3Dabc_DEF-123=', // gitleaks:allow (token inventado del correo de prueba)
  'xyz.789">Elegir mi clave</a></body></html>',
].join('\r\n');

function mensaje(para, cuerpo, asunto = 'Activa tu cuenta en AguaVigía') {
  const [buzon, dominio] = para.split('@');
  return { To: [{ Mailbox: buzon, Domain: dominio }], Content: { Body: cuerpo, Headers: { Subject: [asunto] } } };
}

test('decodifica quoted-printable: = hexadecimal y saltos de línea suaves', () => {
  assert.equal(decodificarQuotedPrintable('a=3Db=\r\nc'), 'a=bc');
  assert.equal(decodificarQuotedPrintable('sin nada raro'), 'sin nada raro');
});

test('extrae el token del enlace de invitación aunque la línea esté partida', () => {
  assert.equal(extraerToken(CUERPO, 'invitacion'), 'abc_DEF-123xyz.789');
});

test('una ruta completa extrae el token de las pantallas del frontend (suscripciones)', () => {
  const cuerpo = '<a href=3D"http://localhost:5173/avisos/confirmar?token=3Dtok-123">Confirmar</a>';
  assert.equal(extraerToken(cuerpo, '/avisos/confirmar'), 'tok-123');
  assert.equal(extraerToken(cuerpo, '/avisos/baja'), null);
});

test('solo extrae el token de la ruta pedida', () => {
  assert.equal(extraerToken(CUERPO, 'verificar'), null);
});

test('un token con caracteres codificados en la URL se devuelve ya decodificado', () => {
  const cuerpo = '<a href=3D"http://x/api/cuentas/enlaces/invitacion?token=3Da%2Bb%3D">';
  assert.equal(extraerToken(cuerpo, 'invitacion'), 'a+b=');
});

test('indexa el último token de cada destinatario', () => {
  const nuevo = CUERPO.replace('abc_DEF-123=\r\nxyz.789', 'nuevo-token');
  const mapa = tokensPorDestinatario([mensaje('ana@sim.test', nuevo), mensaje('ana@sim.test', CUERPO), mensaje('luis@sim.test', CUERPO)], 'invitacion');
  // Mailhog devuelve primero el más reciente: manda el primero que aparece.
  assert.equal(mapa.get('ana@sim.test'), 'nuevo-token');
  assert.equal(mapa.get('luis@sim.test'), 'abc_DEF-123xyz.789');
  assert.equal(mapa.size, 2);
});

test('ignora los correos sin enlace de esa ruta', () => {
  const mapa = tokensPorDestinatario([mensaje('ana@sim.test', 'sin enlaces')], 'invitacion');
  assert.equal(mapa.size, 0);
});

test('el destinatario se compara en minúsculas', () => {
  const mapa = tokensPorDestinatario([mensaje('Ana@Sim.Test', CUERPO)], 'invitacion');
  assert.ok(mapa.has('ana@sim.test'));
});

test('idsDeCorreosPara elige solo los correos dirigidos al dominio de la simulación', () => {
  const mensajes = [
    { ID: 'a', To: [{ Mailbox: 'vecino-0001', Domain: 'sim.aguavigia.test' }] },
    { ID: 'b', To: [{ Mailbox: 'persona', Domain: 'gmail.com' }] },
    { ID: 'c', To: [{ Mailbox: 'x', Domain: 'otro.com' }, { Mailbox: 'Suscriptor-1', Domain: 'SIM.aguavigia.test' }] },
  ];
  assert.deepEqual(idsDeCorreosPara(mensajes, 'sim.aguavigia.test'), ['a', 'c']);
});
