// Las acciones del guion: lo que el simulador HACE, siempre por la API real y por los mismos caminos que seguiría una persona (registro con
// correo, enlace de Mailhog, sesión, ubicación, reporte con identidad, foto con token, el veedor desde el panel). Lo único que no sale por la API
// pública son las rutas de simulación (/api/sim/**): el reloj y la entrega de los boletines.

import crypto from 'node:crypto';
import { esperarTokens } from './mailhog.mjs';
import { repartirVecinos } from './planes.mjs';
import { boletinDeCorte, boletinDeBajaConfianza, boletinDeAplazamiento } from './plantilla-boletin.mjs';
import { fotoSimulada } from './foto.mjs';
import { ErrorDeApi } from './api.mjs';

export const DOMINIO = 'sim.aguavigia.test';
const claveAleatoria = () => crypto.randomBytes(18).toString('base64url');

/** Ejecuta `tarea` sobre cada elemento con como mucho `n` a la vez. Si una falla, las demás ya lanzadas terminan y se relanza el primer error. */
export async function enParalelo(elementos, n, tarea) {
  let siguiente = 0;
  const errores = [];
  const trabajadores = Array.from({ length: Math.min(n, elementos.length) }, async () => {
    while (siguiente < elementos.length && errores.length === 0) {
      const indice = siguiente++;
      try {
        await tarea(elementos[indice], indice);
      } catch (error) {
        errores.push(error);
      }
    }
  });
  await Promise.all(trabajadores);
  if (errores.length > 0) throw errores[0];
}

// --- el panel -------------------------------------------------------------------------------------------------------------------

const CUENTAS_DEL_PANEL = [
  { id: 'veedor1', rol: 'VEEDOR', nombre: 'Veedora Simulada Uno' },
  { id: 'veedor2', rol: 'VEEDOR', nombre: 'Veedor Simulado Dos' },
  { id: 'observador', rol: 'OBSERVADOR', nombre: 'Observadora Simulada' },
];

export async function iniciarSesionDelPanel(ctx, id) {
  const cuenta = ctx.panel[id];
  const sesion = (await ctx.api.post('/api/veedor/sesion', { correo: cuenta.correo, clave: cuenta.clave })).cuerpo;
  cuenta.token = sesion.token;
}

/** Las sesiones del panel se renuevan tras un salto de reloj: así no dependen de cuánto dure la sesión en el reloj que se aceleró. */
export async function renovarSesionesDelPanel(ctx) {
  ctx.panel.admin = { token: (await ctx.api.sim('POST', '/api/sim/sesion-admin')).cuerpo.token };
  for (const { id } of CUENTAS_DEL_PANEL) if (ctx.panel[id]) await iniciarSesionDelPanel(ctx, id);
}

async function prepararPanel(ctx) {
  ctx.panel.admin = { token: (await ctx.api.sim('POST', '/api/sim/sesion-admin')).cuerpo.token };
  for (const { id, rol, nombre } of CUENTAS_DEL_PANEL) {
    ctx.panel[id] = { correo: `${id}@${DOMINIO}`, clave: claveAleatoria(), token: null };
    await ctx.api.post('/api/veedor/usuarios/invitaciones', { correo: ctx.panel[id].correo, nombre, rol }, { token: ctx.panel.admin.token });
  }
  const tokens = await esperarTokens(ctx.config.mailhog, CUENTAS_DEL_PANEL.map(({ id }) => ctx.panel[id].correo), 'invitacion');
  for (const { id } of CUENTAS_DEL_PANEL) {
    const cuenta = ctx.panel[id];
    await ctx.api.post('/api/cuentas/invitacion', { token: tokens.get(cuenta.correo), clave: cuenta.clave });
    await iniciarSesionDelPanel(ctx, id);
  }
  ctx.log(`panel listo: ADMIN (por la ruta de simulación), 2 VEEDOR y 1 OBSERVADOR invitados y activados por el correo de Mailhog`);
}

// --- los vecinos ----------------------------------------------------------------------------------------------------------------

const RENOVAR_SESION_DE_VECINO_CADA_MINUTOS = 180;

/**
 * El token de sesión del vecino, renovado si lo emitió hace más de 3 horas simuladas. El JWT lo valida el reloj del backend (el acelerado) y caduca
 * a las 8 horas de ese reloj: un vecino que se registró a las 08:00 ya no puede reportar a las 16:00 sin volver a entrar, igual que en la vida real.
 */
export async function conSesionVigente(ctx, vecino) {
  if (ctx.cronometro.minuto - (vecino.sesionMinuto ?? 0) < RENOVAR_SESION_DE_VECINO_CADA_MINUTOS) return vecino.token;
  vecino.token = (await ctx.api.post('/api/vecino/sesion', { correo: vecino.correo, clave: vecino.clave })).cuerpo.token;
  vecino.sesionMinuto = ctx.cronometro.minuto;
  return vecino.token;
}

async function registrarVecinos(ctx, accion) {
  const cantidad = accion.cantidad ?? 300;
  const plan = repartirVecinos({
    cantidad,
    clave: accion['barrios-clave'] ?? {},
    sectores: [...ctx.sectores.keys()],
    semilla: accion.semilla ?? 1,
    fuera: accion['fuera-del-barrio'] ?? 6,
    fraccionVerifican: accion['fraccion-verifican'] ?? 0.8,
  });
  const vecinos = plan.map((p) => ({
    ...p,
    correo: `vecino-${String(p.indice).padStart(4, '0')}@${DOMINIO}`,
    nombre: `Vecino Simulado ${String(p.indice).padStart(4, '0')}`,
    clave: claveAleatoria(),
    token: null,
    usado: false,
  }));
  const concurrencia = accion.concurrencia ?? 16;

  await enParalelo(vecinos, concurrencia, (v) => ctx.api.post('/api/cuentas/vecino', {
    correo: v.correo,
    nombre: v.nombre,
    barrioId: v.barrio,
    consentimiento: { privacidad: true, avisos: v.indice % 5 !== 0 },
  }));

  const tokens = await esperarTokens(ctx.config.mailhog, vecinos.map((v) => v.correo), 'invitacion', { espera: 120_000 });
  await enParalelo(vecinos, concurrencia, async (v) => {
    await ctx.api.post('/api/cuentas/invitacion', { token: tokens.get(v.correo), clave: v.clave });
    v.token = (await ctx.api.post('/api/vecino/sesion', { correo: v.correo, clave: v.clave })).cuerpo.token;
    v.sesionMinuto = ctx.cronometro.minuto;
  });

  let rechazados = 0;
  await enParalelo(vecinos.filter((v) => v.verificar || v.intentoFuera), concurrencia, async (v) => {
    const coordenada = v.intentoFuera ? ctx.puntoFuera(v.barrio) : ctx.puntoDentro(v.barrio);
    const r = await ctx.api.post('/api/vecino/verificacion-barrio', { coordenada, precisionMetros: 10 }, { token: v.token, esperados: [200, 422] });
    if (v.intentoFuera) {
      if (r.estado !== 422) throw new Error(`La verificación desde fuera del barrio de ${v.correo} debía dar 422 y dio ${r.estado}`);
      rechazados++;
    } else {
      if (r.estado !== 200) throw new Error(`La verificación de ${v.correo} en ${v.barrio} dio ${r.estado}`);
      v.verificado = true;
    }
  });

  for (const v of vecinos) {
    if (!ctx.vecinos.has(v.barrio)) ctx.vecinos.set(v.barrio, []);
    ctx.vecinos.get(v.barrio).push(v);
  }
  ctx.estadisticas.vecinosRegistrados += vecinos.length;
  ctx.estadisticas.vecinosVerificados += vecinos.filter((v) => v.verificado).length;
  ctx.estadisticas.verificacionesFueraRechazadas += rechazados;
  ctx.log(`${vecinos.length} vecinos registrados por la API real y activados con el correo; ${vecinos.filter((v) => v.verificado).length} verificaron su barrio y ${rechazados} fueron rechazados por estar fuera (422)`);
}

async function suscribirCorreos(ctx, accion) {
  const barrios = accion.barrios;
  const correos = Array.from({ length: accion.cantidad ?? 3 }, (_, i) => `suscriptor-${i + 1}@${DOMINIO}`);
  for (const correo of correos) await ctx.api.post('/api/suscripciones', { correo, sectorIds: barrios });
  const tokens = await esperarTokens(ctx.config.mailhog, correos, '/avisos/confirmar');
  for (const correo of correos) {
    await ctx.api.post(`/api/suscripciones/confirmar?token=${encodeURIComponent(tokens.get(correo))}`, undefined, { cabeceras: { Accept: 'application/json' } });
    ctx.suscriptores.push({ correo, barrios });
  }
  ctx.log(`${correos.length} correos suscritos (doble confirmación por Mailhog) a ${barrios.join(', ')}`);
}

// --- los boletines --------------------------------------------------------------------------------------------------------------

async function enviarBoletin(ctx, accion) {
  const numero = accion.id;
  const dia = accion.dia === 'manana' ? diaSiguiente(ctx.dia) : ctx.dia;
  let boletin;
  if (accion.plantilla === 'corte') {
    boletin = boletinDeCorte({ numero, dia, desde: accion.desde, hasta: accion.hasta, barrios: accion.barrios.map((b) => ctx.nombreDe(b)) });
  } else if (accion.plantilla === 'baja-confianza') {
    boletin = boletinDeBajaConfianza({ numero, barrio: ctx.nombreDe(accion.barrios[0]) });
  } else if (accion.plantilla === 'aplazamiento') {
    boletin = boletinDeAplazamiento({ numero, dia, barrio: ctx.nombreDe(accion.barrios[0]) });
  } else {
    throw new Error(`La plantilla de boletín '${accion.plantilla}' no existe (corte, baja-confianza, aplazamiento)`);
  }
  const { fecha, contenido, ...resto } = boletin;
  await ctx.api.sim('POST', '/api/sim/boletines', { ...resto, contenido, ...(fecha ? { fecha } : {}) });
  ctx.boletines.set(numero, { plantilla: accion.plantilla, barrios: accion.barrios });
  ctx.log(`boletín ${numero} (${accion.plantilla}) entregado a la ingesta: ${accion.barrios.join(', ')}`);
}

export function diaSiguiente(dia) {
  const [a, m, d] = dia.split('-').map(Number);
  return new Date(Date.UTC(a, m - 1, d + 1)).toISOString().slice(0, 10);
}

async function veedorDescartaPropuesta(ctx, accion) {
  const { cuerpo } = await ctx.api.get('/api/veedor/ingesta/propuestas?tamano=200', { token: ctx.panel.veedor1.token });
  const delBoletin = cuerpo.filter((p) => (p.urlOriginal ?? '').endsWith(`/boletin/${accion.boletin}`));
  if (delBoletin.length === 0) throw new Error(`No hay propuestas pendientes del boletín ${accion.boletin}: ¿salió sola al mapa?`);
  for (const propuesta of delBoletin) await ctx.api.patch(`/api/veedor/ingesta/propuestas/${propuesta.id}/descartar`, undefined, { token: ctx.panel.veedor1.token });
  ctx.log(`el veedor descartó ${delBoletin.length} propuesta(s) del boletín ${accion.boletin}`);
}

// --- los reportes y las fotos ---------------------------------------------------------------------------------------------------

async function reporteIndividual(ctx, accion) {
  const cuerpo = { sectorId: accion.barrio, tipo: accion.tipoReporte };
  let opciones;
  if (accion.identidad === 'vecino') {
    opciones = { token: await conSesionVigente(ctx, ctx.tomarVecinoVerificado(accion.barrio)) };
  } else {
    opciones = { dispositivo: (await ctx.api.post('/api/dispositivos')).cuerpo.token };
    if (accion.conUbicacion) {
      cuerpo.coordenada = ctx.puntoDentro(accion.barrio);
      cuerpo.precisionMetros = 15;
    }
  }
  const r = (await ctx.api.post('/api/reportes', cuerpo, opciones)).cuerpo;
  ctx.guardarReporte(accion.etiqueta, { id: r.id, barrio: accion.barrio, tipo: accion.tipoReporte, verificacion: r.verificacion, subidaToken: r.subidaToken, fotoUrl: null, fotoEstado: r.fotoEstado });
}

async function subirFoto(ctx, accion) {
  const reporte = ctx.reporte(accion.reporte, accion.indice ?? 0);
  const datos = new FormData();
  datos.append('foto', new Blob([fotoSimulada({ semilla: Number.parseInt(reporte.id.slice(-6), 16) || reporte.id.length })], { type: 'image/png' }), 'simulacion.png');
  const r = (await ctx.api.enviarFormulario(`/api/reportes/${reporte.id}/foto`, datos, { cabeceras: { 'X-Subida': reporte.subidaToken } })).cuerpo;
  reporte.fotoUrl = r.fotoUrl;
  reporte.fotoEstado = r.fotoEstado;
  ctx.log(`foto simulada subida al reporte ${accion.reporte}#${accion.indice ?? 0} (${r.fotoEstado})`);
}

async function veedorReportes(ctx, accion) {
  const lista = ctx.reportes.get(accion.etiqueta) ?? [];
  const indices = accion.indices ?? lista.map((_, i) => i);
  for (const i of indices) {
    await ctx.api.patch(`/api/veedor/reportes/${ctx.reporte(accion.etiqueta, i).id}/${accion.accion === 'aprobar' ? 'aprobar' : 'descartar'}`, undefined, { token: ctx.panel.veedor1.token });
  }
  ctx.log(`el veedor ${accion.accion === 'aprobar' ? 'aprobó' : 'descartó'} ${indices.length} reporte(s) '${accion.etiqueta}'`);
}

async function veedorFoto(ctx, accion) {
  const reporte = ctx.reporte(accion.etiqueta, accion.indice ?? 0);
  const ruta = accion.accion === 'aprobar' ? `/api/veedor/reportes/${reporte.id}/aprobar` : `/api/veedor/reportes/${reporte.id}/foto/descartar`;
  await ctx.api.patch(ruta, undefined, { token: ctx.panel.veedor1.token });
  ctx.log(`el veedor ${accion.accion === 'aprobar' ? 'aprobó el reporte (y con él su foto)' : 'descartó solo la foto'} de ${accion.etiqueta}#${accion.indice ?? 0}`);
}

// --- los cortes -----------------------------------------------------------------------------------------------------------------

/** Los cortes de un barrio, el más reciente primero, sin los anulados. */
export async function cortesDe(ctx, barrio, token = ctx.panel.veedor1.token) {
  const { cuerpo } = await ctx.api.get(`/api/veedor/cortes?sectorId=${encodeURIComponent(barrio)}`, { token });
  return cuerpo.filter((c) => c.estado !== 'ANULADO').sort((a, b) => b.inicio.localeCompare(a.inicio));
}

async function corteAbierto(ctx, barrio) {
  const cortes = await cortesDe(ctx, barrio);
  const abierto = cortes.find((c) => !(c.cierres ?? []).some((x) => x.sectorId === barrio) && !['RESTABLECIDO', 'EXPIRADO'].includes(c.estado));
  if (!abierto) throw new Error(`El barrio '${barrio}' no tiene un corte abierto que cerrar`);
  return abierto;
}

async function veedorCierraBarrio(ctx, accion) {
  const corte = await corteAbierto(ctx, accion.barrio);
  await ctx.api.patch(`/api/veedor/cortes/${corte.id}/sectores/${accion.barrio}/cierre`, { horaReal: ctx.instanteDeLaHora(accion.hora).toISOString() }, { token: ctx.panel.veedor1.token });
  ctx.log(`el veedor cerró ${accion.barrio} del corte ${corte.id} a las ${accion.hora}`);
}

async function veedorConfirmaCierre(ctx, accion) {
  const cortes = await cortesDe(ctx, accion.barrio);
  const corte = cortes.find((c) => (c.cierres ?? []).some((x) => x.sectorId === accion.barrio));
  if (!corte) throw new Error(`El barrio '${accion.barrio}' no tiene un cierre que confirmar`);
  await ctx.api.patch(`/api/veedor/cortes/${corte.id}/sectores/${accion.barrio}/confirmacion`, { horaReal: ctx.instanteDeLaHora(accion.hora).toISOString() }, { token: ctx.panel.veedor1.token });
  ctx.log(`el veedor confirmó el cierre de ${accion.barrio} con la hora ${accion.hora}`);
}

async function veedorAnulaCorte(ctx, accion) {
  const cortes = await cortesDe(ctx, accion.barrio);
  if (cortes.length === 0) throw new Error(`El barrio '${accion.barrio}' no tiene un corte que anular`);
  await ctx.api.patch(`/api/veedor/cortes/${cortes[0].id}/anulacion`, { motivo: accion.motivo }, { token: ctx.panel.veedor1.token });
  ctx.log(`el veedor anuló el corte ${cortes[0].id} de ${accion.barrio}: ${accion.motivo}`);
}

async function saltar(ctx, accion) {
  ctx.cronometro.saltar(accion.horas * 60);
  await ctx.fijarReloj();
  await renovarSesionesDelPanel(ctx);
  ctx.log(`el reloj salta ${accion.horas} h`);
}

const MANEJADORES = {
  'preparar-panel': prepararPanel,
  'registrar-vecinos': registrarVecinos,
  'suscribir-correos': suscribirCorreos,
  boletin: enviarBoletin,
  'veedor-descarta-propuesta': veedorDescartaPropuesta,
  'reporte-individual': reporteIndividual,
  foto: subirFoto,
  'veedor-reportes': veedorReportes,
  'veedor-foto': veedorFoto,
  'veedor-cierra-barrio': veedorCierraBarrio,
  'veedor-confirma-cierre': veedorConfirmaCierre,
  'veedor-anula-corte': veedorAnulaCorte,
  saltar,
};

export async function ejecutarAccion(ctx, accion) {
  const manejador = MANEJADORES[accion.tipo];
  if (!manejador) throw new Error(`No sé hacer la acción '${accion.tipo}'`);
  try {
    await manejador(ctx, accion);
  } catch (error) {
    if (error instanceof ErrorDeApi) error.message = `${accion.tipo}: ${error.message}`;
    throw error;
  }
}

