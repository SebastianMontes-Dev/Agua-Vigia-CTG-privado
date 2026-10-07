// Vaciar la base de Redis de la simulación (`simulador reiniciar`). Habla el protocolo de Redis con comandos en línea sobre un socket: son dos
// comandos, y así el simulador no necesita ninguna dependencia más. La base 0 es la de la instancia real y nunca se toca.
//
// FLUSHDB solo se manda DESPUÉS de que SELECT respondió OK: en una sola escritura, un SELECT fallido (base inválida) dejaría a FLUSHDB
// ejecutarse sobre la base por defecto, que es la 0, la de la instancia real.

import net from 'node:net';

export const BASE_VALIDA = (db) => Number.isInteger(db) && db >= 1 && db <= 15;

export function vaciarRedis({ host, port, db }, { espera = 5000 } = {}) {
  if (!BASE_VALIDA(db)) {
    return Promise.reject(new Error(`Redis: la base debe ser un entero entre 1 y 15 (era ${db}); la 0 es la de la instancia real y no se vacía. La simulación usa la 1.`));
  }
  return new Promise((resolver, rechazar) => {
    const socket = net.createConnection({ host, port });
    let respuesta = '';
    let enviadoFlush = false;
    const terminar = (error) => {
      socket.destroy();
      if (error) rechazar(error);
      else resolver();
    };
    socket.setTimeout(espera, () => terminar(new Error(`Redis (${host}:${port}) no respondió en ${espera / 1000} s`)));
    socket.on('error', terminar);
    socket.on('connect', () => socket.write(`SELECT ${db}\r\n`));
    socket.on('data', (datos) => {
      respuesta += datos.toString();
      const lineas = respuesta.split('\r\n').filter((l) => l !== '');
      const error = lineas.find((l) => l.startsWith('-'));
      if (error) return terminar(new Error(`Redis respondió: ${error.slice(1)}`));
      if (!enviadoFlush && lineas.length >= 1) {
        enviadoFlush = true;
        socket.write('FLUSHDB\r\n');
      } else if (lineas.length >= 2) terminar();
    });
  });
}
