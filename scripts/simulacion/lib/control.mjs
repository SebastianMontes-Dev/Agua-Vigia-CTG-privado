// Cómo se controla una simulación en marcha desde otra terminal (`simulador pausar|reanudar|velocidad|saltar`): el comando escribe lo que quiere
// en un documento de la propia base de simulación (colección `sim_control`) y el ejecutor lo lee en cada tick. Sin sockets ni procesos que se
// descubran entre sí: Mongo ya está ahí, y `reiniciar` lo suelta junto con todo lo demás.

export const COLECCION = 'sim_control';
const ID = 'control';

/** Aplica al cronómetro lo que pidió el usuario. Devuelve los minutos que se saltaron (que el ejecutor debe trasladar al reloj del backend). */
export function aplicarControl(cronometro, control) {
  if (control === null || control === undefined) return 0;
  if (control.velocidad !== undefined && control.velocidad !== null) cronometro.fijarVelocidad(control.velocidad);
  if (control.pausado === true) cronometro.pausar();
  if (control.pausado === false) cronometro.reanudar();
  const saltar = control.saltarMinutos ?? 0;
  if (saltar > 0) cronometro.saltar(saltar);
  return saltar > 0 ? saltar : 0;
}

/** Deja una petición para el ejecutor: `{pausado}`, `{velocidad}` o `{saltarMinutos}` (los saltos se acumulan hasta que el ejecutor los consume). */
export async function pedir(db, { pausado, velocidad, saltarMinutos }) {
  const poner = {};
  if (pausado !== undefined) poner.pausado = pausado;
  if (velocidad !== undefined) poner.velocidad = velocidad;
  const operacion = { $set: { ...poner, actualizadoEn: new Date() } };
  if (saltarMinutos !== undefined) operacion.$inc = { saltarMinutos };
  await db.collection(COLECCION).updateOne({ _id: ID }, operacion, { upsert: true });
}

/** Lee lo pedido y consume el salto pendiente (un salto se aplica una sola vez). La pausa y la velocidad quedan hasta que alguien las cambie. */
export async function consumir(db) {
  const control = await db.collection(COLECCION).findOneAndUpdate({ _id: ID }, { $set: { saltarMinutos: 0 } }, { returnDocument: 'before' });
  return control ?? null;
}

/** Borra lo pedido: el ejecutor empieza cada ejecución sin las órdenes de la anterior. */
export async function limpiar(db) {
  await db.collection(COLECCION).deleteOne({ _id: ID });
}

/** El estado que publica el ejecutor para que `simulador estado` lo muestre. */
export async function publicarEstado(db, estado) {
  await db.collection(COLECCION).updateOne({ _id: 'estado' }, { $set: { ...estado, actualizadoEn: new Date() } }, { upsert: true });
}

export async function leerEstado(db) {
  return db.collection(COLECCION).findOne({ _id: 'estado' });
}
