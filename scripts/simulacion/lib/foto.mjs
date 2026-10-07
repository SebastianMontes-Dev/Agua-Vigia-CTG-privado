// Fotos de la simulación: una imagen plana con la leyenda «SIMULACIÓN», generada aquí (PNG de 8 bits RGB, sin dependencias). No es una foto de
// nadie ni de ningún barrio: es evidencia falsa declarada como tal. La semilla cambia el color de fondo para que cada foto tenga su propia
// huella SHA-256 (el backend rechaza una foto repetida).

import zlib from 'node:zlib';

// Letra de 5 columnas por 7 filas; Ó lleva su tilde en una fila extra encima.
const LETRAS = {
  S: ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'],
  I: ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '#####'],
  M: ['#...#', '##.##', '#.#.#', '#.#.#', '#...#', '#...#', '#...#'],
  U: ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
  L: ['#....', '#....', '#....', '#....', '#....', '#....', '#####'],
  A: ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
  C: ['.###.', '#...#', '#....', '#....', '#....', '#...#', '.###.'],
  O: ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
  N: ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'],
};
const TILDE = '...#.';
const LEYENDA = ['S', 'I', 'M', 'U', 'L', 'A', 'C', 'I', 'Ó', 'N'];

const tablaCrc = (() => {
  const tabla = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    tabla[n] = c >>> 0;
  }
  return tabla;
})();

function crc32(buffer) {
  let c = 0xffffffff;
  for (const byte of buffer) c = tablaCrc[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function trozo(tipo, datos) {
  const cabecera = Buffer.alloc(8);
  cabecera.writeUInt32BE(datos.length, 0);
  cabecera.write(tipo, 4, 'ascii');
  const cola = Buffer.alloc(4);
  cola.writeUInt32BE(crc32(Buffer.concat([cabecera.subarray(4), datos])), 0);
  return Buffer.concat([cabecera, datos, cola]);
}

/** Las filas de píxeles de la leyenda (true = tinta), de la tilde hacia abajo. */
function mapaDeLaLeyenda() {
  const filas = Array.from({ length: 8 }, () => []);
  LEYENDA.forEach((letra, indice) => {
    const base = letra === 'Ó' ? 'O' : letra;
    const glifo = [letra === 'Ó' ? TILDE : '.....', ...LETRAS[base]];
    glifo.forEach((fila, y) => {
      for (const celda of fila) filas[y].push(celda === '#');
      if (indice < LEYENDA.length - 1) filas[y].push(false); // espacio entre letras
    });
  });
  return filas;
}

export function fotoSimulada({ ancho = 320, alto = 200, semilla = 0 } = {}) {
  const fondo = [40 + ((semilla * 53) % 120), 60 + ((semilla * 97) % 100), 90 + ((semilla * 31) % 120)];
  const mapa = mapaDeLaLeyenda();
  const escala = Math.max(1, Math.floor((ancho * 0.8) / mapa[0].length));
  const origenX = Math.floor((ancho - mapa[0].length * escala) / 2);
  const origenY = Math.floor((alto - mapa.length * escala) / 2);

  const crudo = Buffer.alloc(alto * (1 + ancho * 3));
  for (let y = 0; y < alto; y++) {
    const inicioFila = y * (1 + ancho * 3);
    crudo[inicioFila] = 0; // filtro «ninguno»
    for (let x = 0; x < ancho; x++) {
      const mx = Math.floor((x - origenX) / escala);
      const my = Math.floor((y - origenY) / escala);
      const tinta = mx >= 0 && my >= 0 && my < mapa.length && mx < mapa[0].length && mapa[my][mx];
      const [r, g, b] = tinta ? [255, 255, 255] : fondo;
      const pos = inicioFila + 1 + x * 3;
      crudo[pos] = r;
      crudo[pos + 1] = g;
      crudo[pos + 2] = b;
    }
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(ancho, 0);
  ihdr.writeUInt32BE(alto, 4);
  ihdr[8] = 8; // bits por componente
  ihdr[9] = 2; // RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    trozo('IHDR', ihdr),
    trozo('IDAT', zlib.deflateSync(crudo)),
    trozo('IEND', Buffer.alloc(0)),
  ]);
}
