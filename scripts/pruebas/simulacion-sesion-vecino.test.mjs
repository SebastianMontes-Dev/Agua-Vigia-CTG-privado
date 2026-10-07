import { test } from 'node:test';
import assert from 'node:assert/strict';
import { conSesionVigente } from '../simulacion/lib/acciones.mjs';

function mundo(minuto) {
  const llamadas = [];
  return {
    llamadas,
    ctx: {
      cronometro: { minuto },
      api: {
        post: async (ruta, cuerpo) => {
          llamadas.push({ ruta, cuerpo });
          return { cuerpo: { token: `token-nuevo-${llamadas.length}` } };
        },
      },
    },
  };
}

test('una sesión reciente se reutiliza sin llamar a la API', async () => {
  const { ctx, llamadas } = mundo(100);
  const vecino = { correo: 'a@x', clave: 'c', token: 'viejo', sesionMinuto: 0 };
  assert.equal(await conSesionVigente(ctx, vecino), 'viejo');
  assert.equal(llamadas.length, 0);
});

test('con más de 3 horas simuladas la sesión se renueva (el JWT caduca a las 8 h del reloj acelerado) y se recuerda cuándo', async () => {
  const { ctx, llamadas } = mundo(490);
  const vecino = { correo: 'a@x', clave: 'c', token: 'viejo', sesionMinuto: 0 };
  assert.equal(await conSesionVigente(ctx, vecino), 'token-nuevo-1');
  assert.deepEqual(llamadas, [{ ruta: '/api/vecino/sesion', cuerpo: { correo: 'a@x', clave: 'c' } }]);
  assert.equal(vecino.sesionMinuto, 490);
  assert.equal(await conSesionVigente(ctx, vecino), 'token-nuevo-1'); // y ya no vuelve a renovar
  assert.equal(llamadas.length, 1);
});

test('un vecino sin minuto de sesión registrado se trata como de la hora cero', async () => {
  const { ctx, llamadas } = mundo(200);
  await conSesionVigente(ctx, { correo: 'a@x', clave: 'c', token: 'viejo' });
  assert.equal(llamadas.length, 1);
});
