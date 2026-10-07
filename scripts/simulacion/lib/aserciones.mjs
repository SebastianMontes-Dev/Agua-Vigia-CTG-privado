// Las aserciones del guion (D35): lo que se espera ver al final de cada acto, comprobado contra la API pública, la del panel y, solo para contar
// cuentas, la base de simulación. Cada una espera (con el reloj congelado) a que lo esperado ocurra: el barrido y los correos son asíncronos.

import { leerCorreos } from './mailhog.mjs';

const igual = (a, b) => (a ?? null) === (b ?? null);

async function sector(ctx, barrio) {
  return (await ctx.api.get(`/api/sectores/${encodeURIComponent(barrio)}`)).cuerpo;
}

async function cortesCrudos(ctx, barrio) {
  const { cuerpo } = await ctx.api.get(`/api/veedor/cortes?sectorId=${encodeURIComponent(barrio)}`, { token: ctx.panel.veedor1.token });
  return cuerpo.sort((a, b) => b.inicio.localeCompare(a.inicio));
}

const IMPLEMENTACIONES = {
  modo: (ctx, a) => ctx.esperarHasta(async () => {
    const { modo } = (await ctx.api.get('/api/sistema/modo')).cuerpo;
    return { ok: modo === a.valor, detalle: `modo=${modo}, se esperaba ${a.valor}` };
  }, { espera: 10_000 }),

  'todos-sin-datos': (ctx) => ctx.esperarHasta(async () => {
    const { sectores } = (await ctx.api.get('/api/sectores')).cuerpo;
    const conEstado = sectores.filter((s) => s.estado !== null && s.estado !== undefined);
    return { ok: conEstado.length === 0, detalle: `${conEstado.length} de ${sectores.length} sectores tienen estado (p. ej. ${conEstado.slice(0, 3).map((s) => `${s.id}=${s.estado}`).join(', ')})` };
  }),

  'vecinos-registrados': (ctx, a) => ctx.esperarHasta(async () => {
    const activos = await ctx.db.collection('usuarios').countDocuments({ rol: 'VECINO', estado: 'ACTIVA' });
    const verificados = await ctx.db.collection('usuarios').countDocuments({ rol: 'VECINO', barrioVerificado: true });
    const rechazados = ctx.estadisticas.verificacionesFueraRechazadas;
    const ok = activos >= (a.minimo ?? 0) && verificados >= (a['verificados-minimo'] ?? 0) && rechazados >= (a['fuera-rechazados'] ?? 0);
    return { ok, detalle: `${activos} vecinos activos (mínimo ${a.minimo ?? 0}), ${verificados} con barrio verificado (mínimo ${a['verificados-minimo'] ?? 0}), ${rechazados} rechazados por ubicación fuera del barrio (esperados ${a['fuera-rechazados'] ?? 0})` };
  }, { espera: 15_000 }),

  'estado-sector': (ctx, a) => ctx.esperarHasta(async () => {
    const s = await sector(ctx, a.barrio);
    const fallos = [];
    if ('estado' in a && !igual(s.estado, a.estado)) fallos.push(`estado=${s.estado} (esperado ${a.estado})`);
    if ('origen' in a && !igual(s.origen, a.origen)) fallos.push(`origen=${s.origen} (esperado ${a.origen})`);
    if ('en-disputa' in a && Boolean(s.enDisputa) !== a['en-disputa']) fallos.push(`enDisputa=${s.enDisputa} (esperado ${a['en-disputa']})`);
    if ('por-confirmar' in a && Boolean(s.restablecimientoPorConfirmar) !== a['por-confirmar']) fallos.push(`restablecimientoPorConfirmar=${s.restablecimientoPorConfirmar} (esperado ${a['por-confirmar']})`);
    if ('respaldo-vecinos' in a && (s.respaldo?.vecinos ?? 0) !== a['respaldo-vecinos']) fallos.push(`respaldo.vecinos=${s.respaldo?.vecinos} (esperado ${a['respaldo-vecinos']})`);
    if ('reportes-en-contra-minimo' in a && (s.reportesEnContra ?? 0) < a['reportes-en-contra-minimo']) fallos.push(`reportesEnContra=${s.reportesEnContra} (mínimo ${a['reportes-en-contra-minimo']})`);
    const visto = `estado=${s.estado ?? 'sin datos'}, origen=${s.origen ?? '—'}${s.enDisputa ? ', en disputa' : ''}${s.restablecimientoPorConfirmar ? ', por confirmar' : ''}${s.respaldo ? `, respaldo vecinos=${s.respaldo.vecinos ?? 0}` : ''}`;
    return { ok: fallos.length === 0, detalle: `${a.barrio}: ${fallos.length === 0 ? visto : fallos.join('; ')}` };
  }),

  'sector-sin-verificacion-reciente': (ctx, a) => ctx.esperarHasta(async () => {
    const s = await sector(ctx, a.barrio);
    if (s.estado === null || s.estado === undefined || !s.verificadoEn) return { ok: false, detalle: `${a.barrio} no tiene estado que envejezca (estado=${s.estado})` };
    const horas = (ctx.instanteSimulado().getTime() - new Date(s.verificadoEn).getTime()) / 3_600_000;
    return { ok: horas >= a.horas, detalle: `${a.barrio}: último respaldo hace ${horas.toFixed(1)} h simuladas (se esperaban al menos ${a.horas})` };
  }),

  'bitacora-contiene': (ctx, a) => ctx.esperarHasta(async () => {
    const { cuerpo } = await ctx.api.get(`/api/bitacora?sectorId=${encodeURIComponent(a.barrio)}&tipo=${encodeURIComponent(a['tipo-evento'])}&tamano=50`);
    return { ok: cuerpo.length >= (a.minimo ?? 1), detalle: `${cuerpo.length} evento(s) ${a['tipo-evento']} en la bitácora de ${a.barrio} (mínimo ${a.minimo ?? 1})` };
  }),

  'propuestas-pendientes': (ctx, a) => ctx.esperarHasta(async () => {
    const { cuerpo } = await ctx.api.get('/api/veedor/ingesta/propuestas?tamano=200', { token: ctx.panel.veedor1.token });
    const pendientes = cuerpo.filter((p) => p.estadoRevision === 'PENDIENTE').length;
    const ok = 'cantidad' in a ? pendientes === a.cantidad : pendientes >= (a.minimo ?? 1);
    return { ok, detalle: `${pendientes} propuesta(s) pendiente(s) en la cola del veedor` };
  }),

  disputas: (ctx, a) => ctx.esperarHasta(async () => {
    const { cuerpo } = await ctx.api.get('/api/veedor/disputas', { token: ctx.panel.veedor1.token });
    const esta = cuerpo.some((s) => s.id === a.barrio);
    return { ok: esta === (a.presente ?? true), detalle: `${a.barrio} ${esta ? 'está' : 'no está'} en la cola de disputas (${cuerpo.map((s) => s.id).join(', ') || 'vacía'})` };
  }),

  corte: (ctx, a) => ctx.esperarHasta(async () => {
    const cortes = await cortesCrudos(ctx, a.barrio);
    const corte = a['mas-antiguo'] ? cortes.at(-1) : cortes[0];
    if (!corte) return { ok: false, detalle: `${a.barrio} no tiene cortes` };
    const fallos = [];
    if (a.estado && corte.estado !== a.estado) fallos.push(`estado=${corte.estado} (esperado ${a.estado})`);
    const cierre = (corte.cierres ?? []).find((c) => c.sectorId === a.barrio);
    if ('sin-cierre' in a && Boolean(cierre) === a['sin-cierre']) fallos.push(cierre ? `${a.barrio} tiene cierre y no debía` : `${a.barrio} no tiene cierre y debía`);
    if (a.cierre) {
      if (!cierre) fallos.push(`${a.barrio} no tiene cierre`);
      else {
        if (a.cierre.fuente && cierre.fuente !== a.cierre.fuente) fallos.push(`cierre.fuente=${cierre.fuente} (esperada ${a.cierre.fuente})`);
        if ('provisional' in a.cierre && cierre.provisional !== a.cierre.provisional) fallos.push(`cierre.provisional=${cierre.provisional} (esperado ${a.cierre.provisional})`);
        if (a.cierre.hora) {
          const esperada = ctx.instanteDeLaHora(a.cierre.hora).getTime();
          const diferencia = Math.abs(new Date(cierre.hora).getTime() - esperada) / 60_000;
          if (diferencia > (a.cierre.tolerancia ?? 6)) fallos.push(`cierre.hora=${cierre.hora} (esperada cerca de las ${a.cierre.hora}, a ${diferencia.toFixed(0)} min)`);
        }
      }
    }
    const visto = `estado=${corte.estado}, ${cierre ? `cierre ${cierre.fuente}${cierre.provisional ? ' provisional' : ' confirmado'} a las ${new Date(new Date(cierre.hora).getTime() - 5 * 3600_000).toISOString().slice(11, 16)}` : 'sin cierre'}`;
    return { ok: fallos.length === 0, detalle: `corte de ${a.barrio}: ${fallos.length === 0 ? visto : fallos.join('; ')}` };
  }),

  'cortes-vencidos': (ctx, a) => ctx.esperarHasta(async () => {
    const { cuerpo } = await ctx.api.get('/api/veedor/cortes/vencidos', { token: ctx.panel.veedor1.token });
    const esta = cuerpo.some((c) => c.sectoresAfectados.includes(a.barrio));
    return { ok: esta === (a.presente ?? true), detalle: `${a.barrio} ${esta ? 'está' : 'no está'} en la cola de cortes vencidos (${cuerpo.length} corte(s))` };
  }),

  'senal-de-red': (ctx, a) => ctx.esperarHasta(async () => {
    const { cuerpo } = await ctx.api.get('/api/veedor/reportes/pendientes?tamano=200', { token: ctx.panel.veedor1.token });
    const conSenal = cuerpo.filter((r) => r.sectorId === a.barrio && r.senalRed).length;
    return { ok: conSenal >= (a.minimo ?? 1), detalle: `${conSenal} reporte(s) de ${a.barrio} marcados con señal de red en la cola de moderación (mínimo ${a.minimo ?? 1})` };
  }),

  'foto-publica': (ctx, a) => ctx.esperarHasta(async () => {
    const reporte = ctx.reporte(a.etiqueta, a.indice ?? 0);
    if (!reporte.fotoUrl) return { ok: false, detalle: `el reporte ${a.etiqueta}#${a.indice ?? 0} no tiene foto subida` };
    const respuesta = await ctx.api.get(reporte.fotoUrl, { esperados: [200, 404] });
    const publica = respuesta.estado === 200;
    return { ok: publica === a.publica, detalle: `GET ${reporte.fotoUrl} respondió ${respuesta.estado} (${a.publica ? 'debía ser pública' : 'no debía ser pública'})` };
  }),

  'cumplimiento-sector': (ctx, a) => ctx.esperarHasta(async () => {
    const indice = (await ctx.api.get(`/api/cumplimiento/sectores/${encodeURIComponent(a.barrio)}`)).cuerpo;
    const prometidaH = indice.duracionPrometidaSegundos / 3600;
    const realMin = indice.duracionRealSegundos / 60;
    const tolerancia = a['tolerancia-min'] ?? 6;
    const fallos = [];
    if ('prometido-horas' in a && Math.abs(prometidaH * 60 - a['prometido-horas'] * 60) > tolerancia) fallos.push(`prometido ${prometidaH.toFixed(2)} h (esperado ${a['prometido-horas']} h)`);
    if ('real-minutos' in a && Math.abs(realMin - a['real-minutos']) > tolerancia) fallos.push(`real ${realMin.toFixed(0)} min (esperado ${a['real-minutos']} min)`);
    return { ok: fallos.length === 0, detalle: `Índice de ${a.barrio}: ${fallos.length === 0 ? `prometido ${prometidaH.toFixed(1)} h, real ${realMin.toFixed(0)} min` : fallos.join('; ')}` };
  }),

  'calidad-del-cumplimiento': (ctx, a) => ctx.esperarHasta(async () => {
    const c = (await ctx.api.get('/api/cumplimiento/calidad')).cuerpo;
    const fallos = [];
    if ((c.cortesSinCierreConfirmado ?? 0) < (a['sin-cierre-confirmado-minimo'] ?? 0)) fallos.push(`cortesSinCierreConfirmado=${c.cortesSinCierreConfirmado} (mínimo ${a['sin-cierre-confirmado-minimo']})`);
    if ((c.cortesAnulados ?? 0) < (a['anulados-minimo'] ?? 0)) fallos.push(`cortesAnulados=${c.cortesAnulados} (mínimo ${a['anulados-minimo']})`);
    if ((c.cierresMedidos ?? 0) < (a['cierres-medidos-minimo'] ?? 0)) fallos.push(`cierresMedidos=${c.cierresMedidos} (mínimo ${a['cierres-medidos-minimo']})`);
    return { ok: fallos.length === 0, detalle: `calidad del Índice: ${fallos.length === 0 ? `${c.cierresMedidos} cierre(s) medidos, ${c.cortesSinCierreConfirmado} corte(s) sin cierre confirmado, ${c.cortesAnulados} anulado(s)` : fallos.join('; ')}` };
  }),

  // Un corte anulado no entra al Índice. (Las estadísticas de «barrios más avisados» cuentan AVISOS de Acuacar, no cortes, y no cambian al anular.)
  'indice-excluye-anulados': (ctx, a) => ctx.esperarHasta(async () => {
    const r = await ctx.api.get(`/api/cumplimiento/sectores/${encodeURIComponent(a.barrio)}`, { esperados: [200, 400] });
    return { ok: r.estado === 400, detalle: r.estado === 400 ? `${a.barrio} no tiene cortes cerrados en el Índice: el anulado no cuenta` : `${a.barrio} ya tiene un Índice de ${r.cuerpo?.cantidadCortes ?? '?'} corte(s) cerrado(s)` };
  }),

  'avisos-por-correo': (ctx, a) => ctx.esperarHasta(async () => {
    const correos = await leerCorreos(ctx.config.mailhog);
    const nombre = ctx.nombreDe(a.barrio);
    const pedidos = ctx.suscriptores.filter((s) => s.barrios.includes(a.barrio));
    if (pedidos.length === 0) return { ok: false, detalle: `ningún suscriptor sigue ${a.barrio}` };
    const cuentas = pedidos.map((s) => ({
      correo: s.correo,
      n: correos.filter((m) => (m.To ?? []).some((p) => `${p.Mailbox}@${p.Domain}`.toLowerCase() === s.correo)
        && (m.Content?.Headers?.Subject ?? []).some((asunto) => asunto.toLowerCase().includes(nombre.toLowerCase()) && !asunto.startsWith('Confirma'))).length,
    }));
    const faltan = cuentas.filter((c) => c.n < (a.minimo ?? 1));
    return { ok: faltan.length === 0, detalle: `avisos de ${nombre} por correo: ${cuentas.map((c) => `${c.correo}=${c.n}`).join(', ')} (mínimo ${a.minimo ?? 1} cada uno)` };
  }, { espera: 20_000 }),
};

/** Comprueba una asercion. Devuelve { ok, detalle }. Una excepción de la API cuenta como fallo de la asercion con su mensaje. */
export async function comprobar(ctx, asercion) {
  const implementacion = IMPLEMENTACIONES[asercion.tipo];
  if (!implementacion) return { ok: false, detalle: `No sé comprobar '${asercion.tipo}'` };
  try {
    return await implementacion(ctx, asercion);
  } catch (error) {
    return { ok: false, detalle: error.message };
  }
}
