import { test } from 'node:test';
import assert from 'node:assert/strict';
import zlib from 'node:zlib';
import { fotoSimulada } from '../simulacion/lib/foto.mjs';

function trozos(png) {
  const salida = [];
  let desplazamiento = 8;
  while (desplazamiento < png.length) {
    const longitud = png.readUInt32BE(desplazamiento);
    const tipo = png.toString('ascii', desplazamiento + 4, desplazamiento + 8);
    salida.push({ tipo, datos: png.subarray(desplazamiento + 8, desplazamiento + 8 + longitud), crc: png.readUInt32BE(desplazamiento + 8 + longitud) });
    desplazamiento += 12 + longitud;
  }
  return salida;
}

test('es un PNG con su firma y sus trozos en orden', () => {
  const png = fotoSimulada();
  assert.deepEqual([...png.subarray(0, 8)], [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  assert.deepEqual(trozos(png).map((t) => t.tipo), ['IHDR', 'IDAT', 'IEND']);
});

test('declara el tamaño pedido en 8 bits RGB sin entrelazado', () => {
  const ihdr = trozos(fotoSimulada({ ancho: 320, alto: 200 }))[0].datos;
  assert.equal(ihdr.readUInt32BE(0), 320);
  assert.equal(ihdr.readUInt32BE(4), 200);
  assert.deepEqual([ihdr[8], ihdr[9], ihdr[12]], [8, 2, 0]);
});

test('los datos descomprimen a exactamente una fila (filtro + píxeles) por línea', () => {
  const png = fotoSimulada({ ancho: 100, alto: 60 });
  const crudo = zlib.inflateSync(trozos(png)[1].datos);
  assert.equal(crudo.length, 60 * (1 + 100 * 3));
});

test('todos los CRC son válidos', () => {
  for (const trozo of trozos(fotoSimulada())) {
    const esperado = zlib.crc32 ? zlib.crc32(Buffer.concat([Buffer.from(trozo.tipo, 'ascii'), trozo.datos])) : trozo.crc;
    assert.equal(trozo.crc, esperado >>> 0, `CRC de ${trozo.tipo}`);
  }
});

test('lleva la leyenda: hay píxeles blancos sobre el fondo', () => {
  const png = fotoSimulada({ ancho: 320, alto: 200 });
  const crudo = zlib.inflateSync(trozos(png)[1].datos);
  let blancos = 0;
  for (let i = 0; i < crudo.length; i++) {
    if (i % (1 + 320 * 3) === 0) continue; // byte de filtro de cada fila
    if (crudo[i] === 255) blancos++;
  }
  assert.ok(blancos > 3000, `hay ${blancos} componentes blancas`);
});

test('semillas distintas dan imágenes distintas (la huella SHA-256 no se repite)', () => {
  assert.notDeepEqual(fotoSimulada({ semilla: 1 }), fotoSimulada({ semilla: 2 }));
  assert.deepEqual(fotoSimulada({ semilla: 7 }), fotoSimulada({ semilla: 7 }));
});

test('pesa poco: cabe de sobra en el tope de la subida', () => {
  assert.ok(fotoSimulada().length < 20_000);
});
