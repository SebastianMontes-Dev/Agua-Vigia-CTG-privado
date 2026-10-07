// Los argumentos de `simulador <comando> [opciones]`. Se valida todo aquí, antes de tocar la API o la base.

const COMANDOS = ['iniciar', 'pausar', 'reanudar', 'velocidad', 'saltar', 'estado', 'reiniciar', 'ayuda'];
const SIN_ARGUMENTOS = new Set(['pausar', 'reanudar', 'estado', 'reiniciar']);

function numeroPositivo(texto, que) {
  const numero = Number(String(texto ?? '').replace(/^x/i, '').replace(',', '.'));
  if (!Number.isFinite(numero) || numero <= 0) throw new Error(`${que} debe ser un número mayor que 0 (era '${texto}')`);
  return numero;
}

export function analizarArgumentos(argv) {
  const [comando, ...resto] = argv;
  if (comando === undefined || comando === 'ayuda' || comando === '--ayuda' || comando === '-h') return { comando: 'ayuda' };
  if (!COMANDOS.includes(comando)) throw new Error(`El comando '${comando}' no existe. Comandos: ${COMANDOS.join(', ')}`);
  if (SIN_ARGUMENTOS.has(comando)) return { comando };

  if (comando === 'velocidad') {
    if (resto.length === 0) throw new Error('Falta la velocidad: simulador velocidad <x> (p. ej. 60 o x60)');
    return { comando, velocidad: numeroPositivo(resto[0], 'La velocidad') };
  }
  if (comando === 'saltar') {
    if (resto.length === 0) throw new Error('Faltan las horas: simulador saltar <horas>');
    return { comando, horas: numeroPositivo(resto[0], 'Las horas a saltar') };
  }

  // iniciar
  const opciones = { comando, velocidad: undefined, guion: undefined, reiniciar: false, detenerEnFallo: false };
  for (let i = 0; i < resto.length; i++) {
    const opcion = resto[i];
    if (opcion === '--reiniciar') opciones.reiniciar = true;
    else if (opcion === '--detener-en-fallo') opciones.detenerEnFallo = true;
    else if (opcion === '--velocidad' || opcion === '--guion') {
      if (i + 1 >= resto.length) throw new Error(`La opción ${opcion} necesita un valor`);
      const valor = resto[++i];
      if (opcion === '--velocidad') opciones.velocidad = numeroPositivo(valor, 'La velocidad');
      else opciones.guion = valor;
    } else throw new Error(`La opción '${opcion}' no existe para iniciar (--velocidad, --guion, --reiniciar, --detener-en-fallo)`);
  }
  return opciones;
}
