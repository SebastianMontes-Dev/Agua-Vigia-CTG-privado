// Lo que el ejecutor sabe y recuerda durante una simulación: los sectores y un punto dentro de cada uno, los vecinos que registró, las sesiones del
// panel, los reportes con su etiqueta y los boletines que envió. Las acciones lo llenan y las aserciones lo consultan.

import { instanteDeMinuto } from './guion.mjs';
import { puntosPorBarrio } from '../../lib/identidad-api.mjs';

const pausa = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

/** El día siguiente al de hoy en Cartagena (AAAA-MM-DD): el guion simula «mañana» para que nada quede en el pasado de la base real. */
export function diaDeManana(ahora = new Date()) {
  const cartagena = new Date(ahora.getTime() - 5 * 3600_000 + 24 * 3600_000);
  return cartagena.toISOString().slice(0, 10);
}

export class Contexto {
  constructor({ config, api, db, cronometro, dia, hora, registrar }) {
    this.config = config;
    this.api = api;
    this.db = db;
    this.cronometro = cronometro;
    this.dia = dia;
    this.hora = hora;
    this.registrar = registrar ?? (() => {});
    this.sectores = new Map(); // slug → SectorRespuesta
    this.puntos = new Map(); // slug → { latitud, longitud } dentro del barrio
    this.vecinos = new Map(); // slug → [{ correo, clave, barrio, token, verificado, usado }]
    this.panel = {}; // admin | veedor1 | veedor2 | observador → { correo, clave, token }
    this.reportes = new Map(); // etiqueta → [{ id, barrio, tipo, verificacion, subidaToken, fotoUrl, fotoEstado }]
    this.boletines = new Map(); // id → { plantilla, barrios, enviadoEnMinuto }
    this.suscriptores = []; // [{ correo, barrios }]
    this.estadisticas = { vecinosRegistrados: 0, vecinosVerificados: 0, verificacionesFueraRechazadas: 0 };
  }

  log(texto) {
    this.registrar(texto);
  }

  /** El instante simulado de ahora (lo que marca el reloj del backend). */
  instanteSimulado() {
    return instanteDeMinuto(this.dia, this.hora, this.cronometro.minuto);
  }

  instanteDeLaHora(hhmm) {
    const [h, m] = hhmm.split(':').map(Number);
    const [h0, m0] = this.hora.split(':').map(Number);
    return instanteDeMinuto(this.dia, this.hora, h * 60 + m - (h0 * 60 + m0));
  }

  /** Pone el reloj del backend donde está el cronómetro. */
  async fijarReloj() {
    await this.api.sim('POST', '/api/sim/reloj', { instante: this.instanteSimulado().toISOString() });
  }

  nombreDe(slug) {
    const sector = this.sectores.get(slug);
    if (!sector) throw new Error(`El barrio '${slug}' no existe en la instancia`);
    return sector.nombre;
  }

  puntoDentro(slug) {
    const punto = this.puntos.get(slug);
    if (!punto) throw new Error(`No hay un punto dentro del barrio '${slug}'`);
    return punto;
  }

  /** Un punto que cae en otro barrio distinto de `slug` (para que la verificación de barrio lo rechace). */
  puntoFuera(slug) {
    for (const [otro, punto] of this.puntos) if (otro !== slug) return punto;
    throw new Error('No hay otro barrio de donde sacar una ubicación');
  }

  tomarVecinoVerificado(barrio) {
    const pool = this.vecinos.get(barrio) ?? [];
    const vecino = pool.find((v) => v.verificado && !v.usado);
    if (!vecino) throw new Error(`No quedan vecinos verificados sin usar en '${barrio}': súbelos en 'registrar-vecinos'`);
    vecino.usado = true;
    return vecino;
  }

  guardarReporte(etiqueta, reporte) {
    if (!this.reportes.has(etiqueta)) this.reportes.set(etiqueta, []);
    this.reportes.get(etiqueta).push(reporte);
  }

  reporte(etiqueta, indice = 0) {
    const lista = this.reportes.get(etiqueta);
    if (!lista || !lista[indice]) throw new Error(`No hay un reporte '${etiqueta}' con índice ${indice}`);
    return lista[indice];
  }

  /**
   * Repite `condicion` hasta que dé verdadero o se acabe la espera. Mantiene el reloj del backend en su sitio mientras tanto: sin eso corre a
   * velocidad real entre tick y tick y las aserciones verían una hora que no es la del acto.
   */
  async esperarHasta(condicion, { espera = 30_000, intervalo = 400 } = {}) {
    const limite = Date.now() + espera;
    let ultimo;
    for (;;) {
      ultimo = await condicion();
      if (ultimo === true || (ultimo && ultimo.ok === true)) return { ok: true, detalle: ultimo?.detalle ?? '' };
      if (Date.now() > limite) return { ok: false, detalle: ultimo?.detalle ?? String(ultimo) };
      await this.fijarReloj();
      await pausa(intervalo);
    }
  }
}

export async function crearContexto(opciones) {
  const contexto = new Contexto(opciones);
  const { sectores } = (await contexto.api.get('/api/sectores')).cuerpo;
  for (const sector of sectores) contexto.sectores.set(sector.id, sector);
  for (const [slug, punto] of await puntosPorBarrio(contexto.api.base)) contexto.puntos.set(slug, punto);
  return contexto;
}
