// El bucle que recorre la línea de tiempo del guion a la velocidad pedida. Cada tick (250 ms reales): recoge lo que pidió el usuario desde otra
// terminal (pausa, velocidad, salto), avanza el cronómetro, RE-FIJA el reloj del backend (entre dos ticks el backend corre a 1x y se atrasaría) y
// ejecuta lo que ya tocaba. Las sesiones del panel se renuevan tras cada salto y cada 3 horas simuladas: el JWT usa el reloj del backend y caduca a las 8. Mientras corre una acción o las aserciones de un acto, el cronómetro se CONGELA: a x300 lo que tardan no debe contar
// como tiempo simulado, o el guion se adelantaría a sí mismo.
//
// Todo lo que toca el mundo (reloj, espera, control, acciones, aserciones) entra por parámetro para poder probar el bucle sin red ni espera.

import { aplicarControl } from './control.mjs';
import { construirLineaDeTiempo } from './linea-de-tiempo.mjs';

const horaDeCartagena = (instante) => new Date(instante.getTime() - 5 * 3600_000).toISOString().slice(11, 16);

function etiquetaDe(elemento) {
  const partes = [elemento.tipo, elemento.barrio ?? elemento.etiqueta ?? elemento.id].filter((p) => typeof p === 'string');
  return partes.length > 1 ? `${partes[0]} (${partes[1]})` : partes[0];
}

export async function ejecutarGuion({
  guion,
  ctx,
  ejecutarAccion,
  comprobar,
  control,
  renovarSesiones,
  tickMs = 250,
  detenerEnFallo = false,
  ahora = Date.now,
  dormir = (ms) => new Promise((resolver) => setTimeout(resolver, ms)),
  imprimir = console.log,
}) {
  const linea = construirLineaDeTiempo(guion);
  const actosPorId = new Map(guion.actos.map((a) => [a.id, a]));
  const resultado = { actos: [], fallos: 0, detenido: false, abortado: false };
  const cronometro = ctx.cronometro;
  const CADA_MINUTOS = 180;
  let ultimaRenovacion = 0;
  let actoActual = guion.actos[0]?.id ?? '';
  let fase = 'corriendo';

  const publicar = () =>
    control.publicar({
      minuto: cronometro.minuto,
      hora: horaDeCartagena(ctx.instanteSimulado()),
      velocidad: cronometro.velocidad,
      pausado: cronometro.pausado,
      acto: actoActual,
      fase: fase === 'corriendo' && cronometro.pausado ? 'pausada' : fase,
    });

  // Cada elemento corre con el tiempo congelado y, al acabar, el reloj del backend vuelve a donde está el cronómetro.
  async function congelado(trabajo) {
    cronometro.congelar();
    try {
      return await trabajo();
    } finally {
      cronometro.descongelar();
      await ctx.fijarReloj();
    }
  }

  await control.limpiar();
  await ctx.fijarReloj();
  let ultimo = ahora();
  let indice = 0;

  while (indice < linea.length) {
    await dormir(tickMs);
    const t = ahora();
    const dt = t - ultimo;
    ultimo = t;

    const saltados = aplicarControl(cronometro, await control.consumir());
    cronometro.avanzar(dt);
    await ctx.fijarReloj();
    if (saltados > 0 || cronometro.minuto - ultimaRenovacion >= CADA_MINUTOS) {
      await renovarSesiones(ctx);
      ultimaRenovacion = cronometro.minuto;
    }

    while (indice < linea.length && linea[indice].minuto <= cronometro.minuto) {
      const elemento = linea[indice++];
      actoActual = elemento.acto;
      await publicar();

      if (elemento.tipo === 'accion') {
        try {
          await congelado(() => ejecutarAccion(ctx, elemento.accion));
        } catch (error) {
          imprimir(`  ✗ ${etiquetaDe(elemento.accion)} — ${error.message}`);
          resultado.fallos++;
          resultado.abortado = true;
          fase = 'abortada';
          await publicar();
          return resultado;
        }
      } else {
        const acto = actosPorId.get(elemento.acto);
        const aserciones = [];
        imprimir(`\n■ Acto «${acto.titulo}» — minuto ${Math.round(elemento.minuto)}, ${horaDeCartagena(ctx.instanteSimulado())}`);
        await congelado(async () => {
          for (const asercion of elemento.aserciones) {
            const r = await comprobar(ctx, asercion);
            aserciones.push({ tipo: asercion.tipo, ok: r.ok, detalle: r.detalle });
            imprimir(`  ${r.ok ? '✓' : '✗'} ${etiquetaDe(asercion)}${r.detalle ? ` — ${r.detalle}` : ''}`);
          }
        });
        resultado.actos.push({ id: acto.id, titulo: acto.titulo, aserciones });
        const fallidas = aserciones.filter((a) => !a.ok).length;
        resultado.fallos += fallidas;
        if (fallidas > 0 && detenerEnFallo) {
          resultado.detenido = true;
          fase = 'detenida';
          await publicar();
          return resultado;
        }
      }
      ultimo = ahora(); // lo que tardó el elemento no cuenta como tiempo del guion
    }
    await publicar();
  }

  fase = 'terminada';
  await publicar();
  return resultado;
}
