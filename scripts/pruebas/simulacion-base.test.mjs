import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import net from 'node:net';
import { leerConfig, exigirBaseDeSimulacion } from '../simulacion/lib/config.mjs';
import { ClienteApi, ErrorDeApi } from '../simulacion/lib/api.mjs';
import { aplicarControl } from '../simulacion/lib/control.mjs';
import { Cronometro } from '../simulacion/lib/cronometro.mjs';
import { vaciarRedis } from '../simulacion/lib/redis.mjs';

// --- configuración y salvaguarda ---------------------------------------------------------------------------------------------

test('la configuración por defecto apunta a la simulación en local', () => {
  const c = leerConfig({});
  assert.equal(c.api, 'http://localhost:8082');
  assert.equal(c.mongoDb, 'aguavigia_sim');
  assert.equal(c.redis.db, 1);
  assert.equal(c.mailhog, 'http://localhost:8025');
  assert.equal(c.clave, '');
});

test('lee el entorno', () => {
  const c = leerConfig({ API_URL: 'http://backend-sim:8080', SIMULACION_CLAVE: 'k', MONGODB_DB: 'otra_sim', REDIS_DB: '3', REDIS_HOST: 'redis' });
  assert.deepEqual([c.api, c.clave, c.mongoDb, c.redis.db, c.redis.host], ['http://backend-sim:8080', 'k', 'otra_sim', 3, 'redis']);
});

test('se niega a tocar la base real, aunque se lo pidan', () => {
  assert.throws(() => exigirBaseDeSimulacion(leerConfig({ MONGODB_DB: 'aguavigia' })), /aguavigia_sim|simulaci/i);
  assert.throws(() => exigirBaseDeSimulacion(leerConfig({ REDIS_DB: '0' })), /Redis/);
  assert.doesNotThrow(() => exigirBaseDeSimulacion(leerConfig({})));
});

test('quita la barra final de la URL de la API', () => {
  assert.equal(leerConfig({ API_URL: 'http://x:1/' }).api, 'http://x:1');
});

// --- cliente HTTP ------------------------------------------------------------------------------------------------------------

async function conServidor(manejador, prueba) {
  const peticiones = [];
  const servidor = http.createServer((req, res) => {
    const trozos = [];
    req.on('data', (t) => trozos.push(t));
    req.on('end', () => {
      const cuerpo = Buffer.concat(trozos).toString();
      peticiones.push({ metodo: req.method, ruta: req.url, cabeceras: req.headers, cuerpo });
      manejador(req, res, cuerpo);
    });
  });
  await new Promise((r) => servidor.listen(0, '127.0.0.1', r));
  try {
    await prueba(`http://127.0.0.1:${servidor.address().port}`, peticiones);
  } finally {
    servidor.close();
  }
}

const json = (res, estado, objeto) => {
  res.writeHead(estado, { 'Content-Type': estado >= 400 ? 'application/problem+json' : 'application/json' });
  res.end(JSON.stringify(objeto));
};

test('el cliente manda la clave de simulación solo en las rutas de simulación', async () => {
  await conServidor((req, res) => json(res, 200, { ok: true }), async (base, peticiones) => {
    const api = new ClienteApi({ base, clave: 'la-clave' });
    await api.sim('POST', '/api/sim/reloj', { avanzarSegundos: 60 });
    await api.get('/api/sectores');
    assert.equal(peticiones[0].cabeceras['x-sim-key'], 'la-clave');
    assert.equal(peticiones[0].cabeceras['content-type'], 'application/json');
    assert.deepEqual(JSON.parse(peticiones[0].cuerpo), { avanzarSegundos: 60 });
    assert.equal(peticiones[1].cabeceras['x-sim-key'], undefined, 'la clave no viaja a rutas públicas');
  });
});

test('manda la sesión y la identidad de dispositivo cuando se pide', async () => {
  await conServidor((req, res) => json(res, 200, {}), async (base, peticiones) => {
    const api = new ClienteApi({ base, clave: '' });
    await api.post('/api/reportes', { tipo: 'SIN_AGUA' }, { token: 'jwt' });
    await api.post('/api/reportes', { tipo: 'SIN_AGUA' }, { dispositivo: 'disp' });
    assert.equal(peticiones[0].cabeceras.authorization, 'Bearer jwt');
    assert.equal(peticiones[1].cabeceras['x-dispositivo'], 'disp');
  });
});

test('un 4xx lanza un ErrorDeApi con el estado y el problema (RFC 7807)', async () => {
  await conServidor((req, res) => json(res, 422, { type: 'x/ubicacion-fuera-del-barrio', title: 'Fuera', status: 422 }), async (base) => {
    const api = new ClienteApi({ base, clave: '' });
    await assert.rejects(api.post('/api/vecino/verificacion-barrio', {}), (e) => e instanceof ErrorDeApi && e.estado === 422 && /ubicacion-fuera-del-barrio/.test(e.tipo));
  });
});

test('se pueden tolerar estados concretos', async () => {
  await conServidor((req, res) => json(res, 422, { type: 'x/fuera' }), async (base) => {
    const api = new ClienteApi({ base, clave: '' });
    const r = await api.post('/api/vecino/verificacion-barrio', {}, { esperados: [200, 422] });
    assert.equal(r.estado, 422);
  });
});

test('una respuesta sin cuerpo no rompe (204)', async () => {
  await conServidor((req, res) => { res.writeHead(204); res.end(); }, async (base) => {
    const r = await new ClienteApi({ base, clave: '' }).post('/api/cuentas/invitacion', { token: 't', clave: 'c' });
    assert.equal(r.estado, 204);
    assert.equal(r.cuerpo, null);
  });
});

test('puede mandar un archivo como multipart con cabeceras propias', async () => {
  await conServidor((req, res) => json(res, 200, {}), async (base, peticiones) => {
    const api = new ClienteApi({ base, clave: '' });
    const datos = new FormData();
    datos.append('archivo', new Blob([Buffer.from([1, 2, 3])], { type: 'image/png' }), 'foto.png');
    await api.enviarFormulario('/api/reportes/r1/foto', datos, { cabeceras: { 'X-Subida': 'tok' } });
    assert.match(peticiones[0].cabeceras['content-type'], /^multipart\/form-data; boundary=/);
    assert.equal(peticiones[0].cabeceras['x-subida'], 'tok');
  });
});

// --- control entre terminales ------------------------------------------------------------------------------------------------

test('aplicar el control pausa, reanuda y cambia la velocidad', () => {
  const c = new Cronometro({ velocidad: 60 });
  aplicarControl(c, { pausado: true });
  assert.equal(c.pausado, true);
  aplicarControl(c, { pausado: false, velocidad: 300 });
  assert.deepEqual([c.pausado, c.velocidad], [false, 300]);
});

test('el salto se aplica una vez y devuelve cuántos minutos saltó', () => {
  const c = new Cronometro({ velocidad: 60 });
  const saltados = aplicarControl(c, { saltarMinutos: 120 });
  assert.equal(saltados, 120);
  assert.equal(c.minuto, 120);
  assert.equal(aplicarControl(c, {}), 0);
});

test('un control vacío o nulo no cambia nada', () => {
  const c = new Cronometro({ velocidad: 60 });
  assert.equal(aplicarControl(c, null), 0);
  assert.equal(aplicarControl(c, {}), 0);
  assert.deepEqual(c.estado(), { minuto: 0, velocidad: 60, pausado: false });
});

// --- redis -------------------------------------------------------------------------------------------------------------------

test('vaciar redis manda SELECT y FLUSHDB de la base indicada', async () => {
  const recibidos = [];
  const servidor = net.createServer((socket) => {
    socket.on('data', (d) => {
      recibidos.push(d.toString());
      socket.write('+OK\r\n+OK\r\n');
    });
  });
  await new Promise((r) => servidor.listen(0, '127.0.0.1', r));
  try {
    await vaciarRedis({ host: '127.0.0.1', port: servidor.address().port, db: 1 });
    assert.equal(recibidos.join(''), 'SELECT 1\r\nFLUSHDB\r\n');
  } finally {
    servidor.close();
  }
});

test('vaciar redis se niega a tocar la base 0 (la de la instancia real)', async () => {
  await assert.rejects(vaciarRedis({ host: '127.0.0.1', port: 1, db: 0 }), /base 0|real/);
});

test('vaciar redis falla con el error de Redis si responde con uno', async () => {
  const servidor = net.createServer((socket) => socket.on('data', () => socket.write('-ERR DB index is out of range\r\n+OK\r\n')));
  await new Promise((r) => servidor.listen(0, '127.0.0.1', r));
  try {
    await assert.rejects(vaciarRedis({ host: '127.0.0.1', port: servidor.address().port, db: 1 }), /out of range/);
  } finally {
    servidor.close();
  }
});

test('una base de Redis que no es un entero entre 1 y 15 no pasa la guardia (un NaN no es 0 y se colaría)', () => {
  const base = leerConfig({ MONGODB_DB: 'aguavigia_sim' });
  for (const db of [Number.NaN, -1, 0, 1.5, 16, 100]) {
    assert.throws(() => exigirBaseDeSimulacion({ ...base, redis: { ...base.redis, db } }), /Redis/, `db=${db}`);
  }
  assert.doesNotThrow(() => exigirBaseDeSimulacion({ ...base, redis: { ...base.redis, db: 1 } }));
});

test('vaciarRedis se niega con una base inválida sin abrir ninguna conexión', async () => {
  for (const db of [Number.NaN, -1, 0, 1.5, 16]) {
    await assert.rejects(vaciarRedis({ host: '127.0.0.1', port: 1, db }), /Redis/, `db=${db}`);
  }
});

test('vaciarRedis espera el OK de SELECT antes de mandar FLUSHDB (nunca en la misma escritura)', async () => {
  const recibido = [];
  const servidor = net.createServer((socket) => {
    socket.on('data', (datos) => {
      for (const linea of datos.toString().split('\r\n').filter(Boolean)) {
        recibido.push(linea);
        if (linea.startsWith('SELECT')) socket.write('+OK\r\n');
        if (linea === 'FLUSHDB') socket.write('+OK\r\n');
      }
    });
  });
  await new Promise((r) => servidor.listen(0, '127.0.0.1', r));
  await vaciarRedis({ host: '127.0.0.1', port: servidor.address().port, db: 1 });
  servidor.close();
  assert.deepEqual(recibido, ['SELECT 1', 'FLUSHDB']);
});

test('si SELECT falla, FLUSHDB no se envía', async () => {
  const recibido = [];
  const servidor = net.createServer((socket) => {
    socket.on('data', (datos) => {
      for (const linea of datos.toString().split('\r\n').filter(Boolean)) {
        recibido.push(linea);
        if (linea.startsWith('SELECT')) socket.write('-ERR DB index is out of range\r\n');
      }
    });
  });
  await new Promise((r) => servidor.listen(0, '127.0.0.1', r));
  await assert.rejects(vaciarRedis({ host: '127.0.0.1', port: servidor.address().port, db: 1 }), /out of range/);
  servidor.close();
  assert.deepEqual(recibido, ['SELECT 1']);
});
