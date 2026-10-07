// Simulador de AguaVigía: corre un guion de un día entero contra la instancia de SIMULACIÓN (backend-sim, base aguavigia_sim, Redis db 1) y
// comprueba lo que debe pasar. Nunca apunta a la instancia real: `exigirBaseDeSimulacion` se niega si la base no acaba en _sim o Redis es la 0.
//
//   node simulacion/simulador.mjs iniciar [--velocidad 60] [--guion archivo] [--reiniciar] [--detener-en-fallo]
//   node simulacion/simulador.mjs pausar | reanudar | velocidad <x> | saltar <horas> | estado | reiniciar | ayuda
//
// Con Docker: docker compose --profile simulacion run --rm simulador iniciar --velocidad 300

import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { MongoClient } from 'mongodb';
import { analizarArgumentos } from './lib/argumentos.mjs';
import { leerConfig, exigirBaseDeSimulacion } from './lib/config.mjs';
import { ClienteApi } from './lib/api.mjs';
import { Cronometro } from './lib/cronometro.mjs';
import { leerGuion } from './lib/guion.mjs';
import { crearContexto, diaDeManana } from './lib/contexto.mjs';
import { ejecutarAccion, renovarSesionesDelPanel, DOMINIO } from './lib/acciones.mjs';
import { comprobar } from './lib/aserciones.mjs';
import { ejecutarGuion } from './lib/ejecutor.mjs';
import { vaciarRedis } from './lib/redis.mjs';
import { exigirInstanciaDeSimulacion } from './lib/instancia.mjs';
import { vaciarCorreosDeSimulacion } from './lib/mailhog.mjs';
import * as control from './lib/control.mjs';

const GUION_POR_DEFECTO = fileURLToPath(new URL('./guion-completo.yaml', import.meta.url));
const SEMBRAR_SECTORES = fileURLToPath(new URL('../sembrar-sectores.mjs', import.meta.url));
const SECTORES_ESPERADOS = 211;

const AYUDA = `Simulador de AguaVigía (instancia de simulación, nunca la real)

  iniciar [--velocidad 60] [--guion archivo] [--reiniciar] [--detener-en-fallo]
        Corre el guion. Velocidad: minutos simulados por minuto real (1, 10, 60, 300). Si la base de simulación
        trae datos de otra corrida se niega, salvo con --reiniciar. Sale con código distinto de 0 si falla una aserción.
  pausar | reanudar          Detiene o reanuda el tiempo del guion en marcha (desde otra terminal).
  velocidad <x>              Cambia la velocidad del guion en marcha.
  saltar <horas>             Adelanta el reloj simulado esas horas (el barrido y las expiraciones se ven al instante).
  estado                     Muestra en qué minuto y acto va el guion en marcha.
  reiniciar                  Vacía la base de simulación (deja índices y la cuenta ADMIN), Redis db 1 y los correos de la simulación,
                             devuelve el reloj al real y vuelve a sembrar los sectores.
  ayuda                      Esto.
`;

const config = leerConfig();
const sim = () => new ClienteApi({ base: config.api, clave: config.clave });

function exigirClave() {
  if (config.clave.length < 32) {
    throw new Error('Falta SIMULACION_CLAVE (32 caracteres o más). Genérala con: node simulacion/preparar.mjs');
  }
}

async function conBase(trabajo) {
  exigirBaseDeSimulacion(config);
  const cliente = new MongoClient(config.mongoUri, { serverSelectionTimeoutMS: 8000 });
  try {
    await cliente.connect();
    return await trabajo(cliente.db(config.mongoDb));
  } finally {
    await cliente.close();
  }
}

function sembrarSectores() {
  return new Promise((resolver, rechazar) => {
    const proceso = spawn(process.execPath, [SEMBRAR_SECTORES], {
      env: { ...process.env, MONGODB_URI: config.mongoUri, MONGODB_DB: config.mongoDb },
      stdio: ['ignore', 'inherit', 'inherit'],
    });
    proceso.on('error', rechazar);
    proceso.on('exit', (codigo) => (codigo === 0 ? resolver() : rechazar(new Error(`sembrar-sectores terminó con código ${codigo}`))));
  });
}

/** Siembra los sectores y, DESPUÉS, vacía la caché de Redis: el backend guarda la lista de sectores y, si la leyó vacía antes de la siembra, la serviría vacía. */
async function sembrarSectoresYRefrescarCache() {
  await sembrarSectores();
  await vaciarRedis(config.redis);
}

async function asegurarSectores(db) {
  const n = await db.collection('sectores').countDocuments();
  if (n >= SECTORES_ESPERADOS) return;
  console.log(`La base de simulación tiene ${n} sectores (se esperan ${SECTORES_ESPERADOS}): sembrando los sectores reales en '${config.mongoDb}'…`);
  await sembrarSectoresYRefrescarCache();
}

async function hayDatosDeOtraCorrida(db) {
  // Cualquier cuenta que no sea el ADMIN inicial es de una corrida anterior (también las del panel, que un guion abortado deja a medias).
  const [vecinos, cortes, reportes] = await Promise.all([
    db.collection('usuarios').countDocuments({ rol: { $ne: 'ADMIN' } }),
    db.collection('cortes').countDocuments(),
    db.collection('reportes').countDocuments(),
  ]);
  return vecinos + cortes + reportes > 0 ? { vecinos, cortes, reportes } : null;
}

async function reiniciar(db) {
  exigirBaseDeSimulacion(config);
  // No se suelta la base entera: el backend crea los índices y la cuenta ADMIN solo al arrancar.
  const colecciones = (await db.listCollections({}, { nameOnly: true }).toArray()).map((c) => c.name).filter((n) => !n.startsWith('system.'));
  for (const nombre of colecciones) {
    const filtro = nombre === 'usuarios' ? { rol: { $ne: 'ADMIN' } } : {};
    const { deletedCount } = await db.collection(nombre).deleteMany(filtro);
    if (deletedCount > 0) console.log(`  ${nombre}: ${deletedCount} documento(s) borrados`);
  }
  await vaciarRedis(config.redis);
  const correos = await vaciarCorreosDeSimulacion(config.mailhog, DOMINIO);
  console.log(`  Redis db ${config.redis.db} vaciada; ${correos} correo(s) de la simulación borrados de Mailhog`);
  try {
    await sim().sim('POST', '/api/sim/reinicio');
    console.log('  buzón de boletines vaciado y reloj devuelto al real (POST /api/sim/reinicio)');
  } catch (error) {
    console.warn(`  AVISO: no se pudo avisar a la API (${error.message}); si arranca después, usa el reloj real.`);
  }
  await sembrarSectoresYRefrescarCache();
}

async function iniciar(opciones) {
  exigirClave();
  const api = sim();
  await exigirInstanciaDeSimulacion(api);

  const guion = leerGuion(await readFile(opciones.guion ?? GUION_POR_DEFECTO, 'utf8'));
  const velocidad = opciones.velocidad ?? guion.velocidad;

  return conBase(async (db) => {
    const previos = await hayDatosDeOtraCorrida(db);
    if (previos && !opciones.reiniciar) {
      throw new Error(`La base '${config.mongoDb}' ya tiene datos de otra corrida (${previos.vecinos} cuentas, ${previos.cortes} cortes, ${previos.reportes} reportes). Corre 'simulador reiniciar' o pasa --reiniciar.`);
    }
    if (previos) {
      console.log('Reiniciando la simulación anterior…');
      await reiniciar(db);
    }
    await asegurarSectores(db);

    const cronometro = new Cronometro({ velocidad });
    const dia = guion.dia ?? diaDeManana();
    let contexto;
    const registrar = (texto) => console.log(`  [${horaSimulada(contexto)}] ${texto}`);
    contexto = await crearContexto({ config, api, db, cronometro, dia, hora: guion.hora, registrar });

    console.log(`${guion.nombre}: día simulado ${dia} desde las ${guion.hora} (Cartagena), velocidad x${velocidad}`);
    const inicio = Date.now();
    const resultado = await ejecutarGuion({
      guion,
      ctx: contexto,
      ejecutarAccion,
      comprobar,
      renovarSesiones: renovarSesionesDelPanel,
      detenerEnFallo: opciones.detenerEnFallo,
      control: {
        limpiar: () => control.limpiar(db),
        consumir: () => control.consumir(db),
        publicar: (estado) => control.publicarEstado(db, estado),
      },
    });
    mostrarResumen(resultado, Date.now() - inicio);
    return resultado.fallos === 0 && !resultado.abortado ? 0 : 1;
  });
}

function horaSimulada(contexto) {
  return new Date(contexto.instanteSimulado().getTime() - 5 * 3600_000).toISOString().slice(11, 16);
}

function mostrarResumen(resultado, milisegundos) {
  console.log('\n══ Resumen por acto ══');
  for (const acto of resultado.actos) {
    const fallidas = acto.aserciones.filter((a) => !a.ok).length;
    const marca = fallidas === 0 ? '✓' : '✗';
    console.log(`${marca} ${acto.titulo}: ${acto.aserciones.length - fallidas}/${acto.aserciones.length} aserciones`);
  }
  const total = resultado.actos.reduce((n, a) => n + a.aserciones.length, 0);
  const estado = resultado.abortado ? 'ABORTADO por una acción que falló' : resultado.detenido ? 'DETENIDO en el primer fallo' : resultado.fallos === 0 ? 'COMPLETO' : 'COMPLETO con fallos';
  console.log(`\n${estado}: ${total - resultado.fallos}/${total} aserciones correctas, ${(milisegundos / 1000).toFixed(0)} s reales.`);
}

async function estado() {
  return conBase(async (db) => {
    const e = await control.leerEstado(db);
    if (!e) {
      console.log('No hay ninguna simulación en marcha en esta base.');
      return 0;
    }
    const hace = (Date.now() - new Date(e.actualizadoEn).getTime()) / 1000;
    const viva = e.fase === 'terminada' || e.fase === 'abortada' || e.fase === 'detenida' || hace < 10;
    console.log(`Fase: ${e.fase}${viva ? '' : ` (sin noticias hace ${hace.toFixed(0)} s: el ejecutor parece haberse detenido)`}`);
    console.log(`Acto: ${e.acto} · minuto ${e.minuto.toFixed(1)} · hora simulada ${e.hora} · velocidad x${e.velocidad}${e.pausado ? ' · PAUSADA' : ''}`);
    return 0;
  });
}

async function principal() {
  const opciones = analizarArgumentos(process.argv.slice(2));
  switch (opciones.comando) {
    case 'ayuda':
      console.log(AYUDA);
      return 0;
    case 'iniciar':
      return iniciar(opciones);
    case 'pausar':
    case 'reanudar':
      return conBase(async (db) => {
        await control.pedir(db, { pausado: opciones.comando === 'pausar' });
        console.log(opciones.comando === 'pausar' ? 'Pausa pedida.' : 'Reanudar pedido.');
        return 0;
      });
    case 'velocidad':
      return conBase(async (db) => {
        await control.pedir(db, { velocidad: opciones.velocidad });
        console.log(`Velocidad x${opciones.velocidad} pedida.`);
        return 0;
      });
    case 'saltar':
      return conBase(async (db) => {
        await control.pedir(db, { saltarMinutos: Math.round(opciones.horas * 60) });
        console.log(`Salto de ${opciones.horas} h pedido.`);
        return 0;
      });
    case 'estado':
      return estado();
    case 'reiniciar':
      exigirClave();
      await exigirInstanciaDeSimulacion(sim());
      return conBase(async (db) => {
        console.log(`Reiniciando la simulación (base '${config.mongoDb}', Redis db ${config.redis.db})…`);
        await reiniciar(db);
        console.log('Listo.');
        return 0;
      });
    default:
      throw new Error(`Comando sin implementar: ${opciones.comando}`);
  }
}

principal().then(
  (codigo) => process.exit(codigo),
  (error) => {
    console.error(`\nERROR: ${error.message}`);
    process.exit(2);
  },
);
