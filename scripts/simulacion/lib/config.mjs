// Dónde está la instancia de simulación y su base. Los valores por defecto sirven para correr el simulador desde el equipo contra
// `docker compose --profile simulacion up` (API en 8082, Mongo en 27017, Redis en 6379, Mailhog en 8025); dentro de compose los pone el servicio
// `simulador`. Nada de aquí apunta nunca a la instancia real: `exigirBaseDeSimulacion` lo comprueba antes de borrar nada.

import { BASE_VALIDA } from './redis.mjs';

export function leerConfig(env = process.env) {
  return {
    api: (env.API_URL ?? 'http://localhost:8082').replace(/\/+$/, ''),
    clave: env.SIMULACION_CLAVE ?? '',
    mongoUri: env.MONGODB_URI ?? 'mongodb://localhost:27017/?directConnection=true',
    mongoDb: env.MONGODB_DB ?? 'aguavigia_sim',
    redis: { host: env.REDIS_HOST ?? 'localhost', port: Number(env.REDIS_PORT ?? 6379), db: Number(env.REDIS_DB ?? 1) },
    mailhog: (env.MAILHOG_URL ?? 'http://localhost:8025').replace(/\/+$/, ''),
  };
}

/** Antes de soltar nada: la base tiene que ser la de simulación y Redis no puede ser la base 0 (la de la instancia real). */
export function exigirBaseDeSimulacion(config) {
  if (!config.mongoDb.endsWith('_sim')) {
    throw new Error(`La base '${config.mongoDb}' no es de simulación (su nombre debe acabar en _sim, p. ej. aguavigia_sim). No se toca.`);
  }
  if (!BASE_VALIDA(config.redis.db)) {
    throw new Error(`Redis: la base debe ser un entero entre 1 y 15 (era ${config.redis.db}); la 0 es la de la instancia real. La simulación usa la base 1 (REDIS_DB=1). No se toca.`);
  }
}
