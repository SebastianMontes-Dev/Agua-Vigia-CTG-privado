// Antes de borrar o de correr nada hay que saber con qué API se habla: la de simulación se declara en /api/sistema/modo. Sin esa comprobación, un
// API_URL o una base mal puestos harían que `reiniciar` vaciara datos de otra instancia.

export async function exigirInstanciaDeSimulacion(api) {
  let modo;
  try {
    modo = (await api.get('/api/sistema/modo')).cuerpo.modo;
  } catch (error) {
    throw new Error(`No se pudo consultar ${api.base}/api/sistema/modo (${error.message}). ¿Está arriba backend-sim? docker compose --profile simulacion up -d backend-sim`);
  }
  if (modo !== 'SIMULACION') throw new Error(`La API en ${api.base} informa modo=${modo}: no es la instancia de simulación. No se toca.`);
}
