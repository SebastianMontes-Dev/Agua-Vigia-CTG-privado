import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Cronometro } from '../simulacion/lib/cronometro.mjs';
import { ejecutarGuion } from '../simulacion/lib/ejecutor.mjs';

/** Un mundo falso y determinista: el «tiempo real» solo avanza cuando el ejecutor duerme o cuando una acción dice cuánto tarda. */
function mundo({ velocidad = 60, pedidos = [] } = {}) {
  const m = {
    ahora: 0,
    cronometro: new Cronometro({ velocidad }),
    eventos: [],
    relojesFijados: [],
    pedidos: [...pedidos],
    estados: [],
    salidas: [],
    sesionesRenovadas: 0,
  };
  m.ctx = {
    db: null,
    cronometro: m.cronometro,
    log: (t) => m.salidas.push(t),
    fijarReloj: async () => m.relojesFijados.push(m.cronometro.minuto),
    instanteSimulado: () => new Date(Date.UTC(2026, 9, 7, 13, 0) + m.cronometro.minuto * 60_000),
  };
  m.opciones = {
    ctx: m.ctx,
    tickMs: 250,
    ahora: () => m.ahora,
    dormir: async (ms) => {
      m.ahora += ms;
    },
    control: {
      limpiar: async () => {},
      consumir: async () => m.pedidos.shift() ?? null,
      publicar: async (estado) => m.estados.push(estado),
    },
    renovarSesiones: async () => {
      m.sesionesRenovadas++;
    },
    imprimir: (t) => m.salidas.push(t),
  };
  return m;
}

const acto = (id, minuto, { duracion = 0, acciones = [], aserciones = [] } = {}) => ({ id, titulo: id, minuto, duracion, acciones, aserciones });

test('ejecuta cada acción cuando su minuto llega, ni antes ni un tick después', async () => {
  const m = mundo(); // x60 con ticks de 250 ms: 0,25 min simulados por tick
  const guion = { actos: [acto('a', 1, { acciones: [{ tipo: 'x' }] })] };
  await ejecutarGuion({
    ...m.opciones,
    guion,
    ejecutarAccion: async (ctx, accion) => m.eventos.push({ tipo: accion.tipo, minuto: m.cronometro.minuto }),
    comprobar: async () => ({ ok: true, detalle: '' }),
  });
  const x = m.eventos.find((e) => e.tipo === 'x');
  assert.ok(x.minuto >= 1 && x.minuto < 1.25, `se ejecutó en el minuto ${x.minuto}`);
});

test('el fin de acto comprueba las aserciones, imprime ✓ o ✗ y cuenta los fallos', async () => {
  const m = mundo();
  const guion = {
    actos: [acto('a', 0, { aserciones: [{ tipo: 'bien' }, { tipo: 'mal' }] }), acto('b', 1, { aserciones: [{ tipo: 'bien' }] })],
  };
  const resultado = await ejecutarGuion({
    ...m.opciones,
    guion,
    ejecutarAccion: async () => {},
    comprobar: async (ctx, a) => (a.tipo === 'bien' ? { ok: true, detalle: 'todo en orden' } : { ok: false, detalle: 'no coincide' }),
  });
  assert.equal(resultado.fallos, 1);
  assert.equal(resultado.actos.length, 2);
  assert.deepEqual(resultado.actos[0].aserciones.map((a) => a.ok), [true, false]);
  const texto = m.salidas.join('\n');
  assert.match(texto, /✓ bien/);
  assert.match(texto, /✗ mal.*no coincide/);
});

test('el cronómetro se congela mientras corre una acción: lo que tarda no cuenta como tiempo simulado', async () => {
  const m = mundo({ velocidad: 300 });
  const guion = { actos: [acto('a', 1, { acciones: [{ tipo: 'lenta' }], aserciones: [{ tipo: 'z' }] })] };
  let antes;
  let despues;
  await ejecutarGuion({
    ...m.opciones,
    guion,
    ejecutarAccion: async () => {
      antes = m.cronometro.minuto;
      await m.opciones.dormir(120_000); // la acción tarda 2 minutos reales (x300 serían 10 simulados)
      despues = m.cronometro.minuto;
    },
    comprobar: async () => ({ ok: true, detalle: '' }),
  });
  assert.equal(antes, despues);
});

test('las aserciones también corren con el tiempo congelado, y lo que tardan no se suma después', async () => {
  const m = mundo({ velocidad: 300 });
  const guion = { actos: [acto('a', 0, { aserciones: [{ tipo: 'z' }] }), acto('b', 100)] };
  const minutos = [];
  await ejecutarGuion({
    ...m.opciones,
    guion,
    ejecutarAccion: async () => {},
    comprobar: async () => {
      minutos.push(m.cronometro.minuto);
      await m.opciones.dormir(60_000);
      minutos.push(m.cronometro.minuto);
      return { ok: true, detalle: '' };
    },
  });
  assert.equal(minutos[0], minutos[1]);
});

test('re-fija el reloj del backend en cada tick, no solo cuando pasa algo', async () => {
  const m = mundo();
  await ejecutarGuion({ ...m.opciones, guion: { actos: [acto('a', 2)] }, ejecutarAccion: async () => {}, comprobar: async () => ({ ok: true, detalle: '' }) });
  // 2 min a x60 = 8 ticks; más el fijado inicial en el minuto 0
  assert.ok(m.relojesFijados.length >= 9, `solo se fijó ${m.relojesFijados.length} veces`);
  assert.equal(m.relojesFijados[0], 0);
});

test('un salto pedido desde otra terminal adelanta el cronómetro, fija el reloj y renueva las sesiones', async () => {
  const m = mundo({ pedidos: [{ saltarMinutos: 60 }] });
  const guion = { actos: [acto('a', 30, { acciones: [{ tipo: 'tarde' }] })] };
  await ejecutarGuion({
    ...m.opciones,
    guion,
    ejecutarAccion: async (ctx, accion) => m.eventos.push({ tipo: accion.tipo, minuto: m.cronometro.minuto }),
    comprobar: async () => ({ ok: true, detalle: '' }),
  });
  assert.equal(m.sesionesRenovadas, 1);
  assert.ok(m.eventos[0].minuto >= 60, 'tras el salto de 60 min la acción del minuto 30 ya está vencida');
});

test('renueva las sesiones cada 3 horas simuladas aunque nadie salte el reloj (el JWT caduca a las 8 h del reloj acelerado)', async () => {
  const m = mundo({ velocidad: 300 });
  await ejecutarGuion({ ...m.opciones, guion: { actos: [acto('a', 400)] }, ejecutarAccion: async () => {}, comprobar: async () => ({ ok: true, detalle: '' }) });
  assert.equal(m.sesionesRenovadas, 2, 'a los minutos ~180 y ~360');
});

test('en pausa el tiempo no avanza y las acciones esperan', async () => {
  const m = mundo({ pedidos: [{ pausado: true }, null, null, null, { pausado: false }] });
  const guion = { actos: [acto('a', 0.25, { acciones: [{ tipo: 'x' }] })] };
  let minutoAlEjecutar;
  await ejecutarGuion({
    ...m.opciones,
    guion,
    ejecutarAccion: async () => {
      minutoAlEjecutar = m.cronometro.minuto;
    },
    comprobar: async () => ({ ok: true, detalle: '' }),
  });
  // 5 ticks consumieron pedidos (la pausa duró 4 y el reanudar entra en el 5.º); solo después de reanudar corre 0,25 min
  assert.ok(m.ahora >= 5 * 250, `terminó demasiado pronto (${m.ahora} ms)`);
  assert.ok(minutoAlEjecutar >= 0.25);
});

test('publica el estado en cada tick para `simulador estado`', async () => {
  const m = mundo();
  await ejecutarGuion({ ...m.opciones, guion: { actos: [acto('a', 1)] }, ejecutarAccion: async () => {}, comprobar: async () => ({ ok: true, detalle: '' }) });
  assert.ok(m.estados.length >= 4);
  assert.deepEqual(Object.keys(m.estados[0]).sort(), ['acto', 'fase', 'hora', 'minuto', 'pausado', 'velocidad']);
  assert.equal(m.estados.at(-1).fase, 'terminada');
});

test('con detenerEnFallo, tras un acto con fallos no ejecuta los siguientes', async () => {
  const m = mundo();
  const guion = { actos: [acto('a', 0, { aserciones: [{ tipo: 'mal' }] }), acto('b', 1, { acciones: [{ tipo: 'no-debe-correr' }] })] };
  const resultado = await ejecutarGuion({
    ...m.opciones,
    guion,
    detenerEnFallo: true,
    ejecutarAccion: async (ctx, accion) => m.eventos.push(accion.tipo),
    comprobar: async () => ({ ok: false, detalle: 'no' }),
  });
  assert.deepEqual(m.eventos, []);
  assert.equal(resultado.detenido, true);
  assert.equal(resultado.fallos, 1);
});

test('sin detenerEnFallo, un acto con fallos no impide los siguientes', async () => {
  const m = mundo();
  const guion = { actos: [acto('a', 0, { aserciones: [{ tipo: 'mal' }] }), acto('b', 1, { acciones: [{ tipo: 'sigue' }] })] };
  await ejecutarGuion({
    ...m.opciones,
    guion,
    ejecutarAccion: async (ctx, accion) => m.eventos.push(accion.tipo),
    comprobar: async () => ({ ok: false, detalle: 'no' }),
  });
  assert.deepEqual(m.eventos, ['sigue']);
});

test('si una acción lanza, el guion se aborta (lo siguiente depende de ella) y queda anotado como fallo', async () => {
  const m = mundo();
  const guion = { actos: [acto('a', 0, { acciones: [{ tipo: 'rota' }] }), acto('b', 1, { acciones: [{ tipo: 'no-debe-correr' }] })] };
  const resultado = await ejecutarGuion({
    ...m.opciones,
    guion,
    ejecutarAccion: async (ctx, accion) => {
      if (accion.tipo === 'rota') throw new Error('la API dijo 500');
      m.eventos.push(accion.tipo);
    },
    comprobar: async () => ({ ok: true, detalle: '' }),
  });
  assert.deepEqual(m.eventos, []);
  assert.equal(resultado.abortado, true);
  assert.ok(resultado.fallos >= 1);
  assert.match(m.salidas.join('\n'), /✗ rota.*la API dijo 500/);
});
