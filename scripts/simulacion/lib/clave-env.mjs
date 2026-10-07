// Pone SIMULACION_CLAVE en el contenido de un .env sin sobrescribir nunca una que ya exista. Pura (el contenido entra y sale como texto) para poder
// probarla; el archivo y el generador de la clave los pone preparar.mjs. No devuelve la clave: lo que se imprima depende de quien llame.

const NOMBRE = 'SIMULACION_CLAVE';
const LARGO_MINIMO = 32;

export function asegurarClaveEnEnv(contenido, generar) {
  const texto = contenido ?? '';
  const salto = texto.includes('\r\n') ? '\r\n' : '\n';
  const lineas = texto === '' ? [] : texto.split(/\r?\n/);
  if (lineas.at(-1) === '') lineas.pop();

  const indice = lineas.findIndex((l) => new RegExp(`^\\s*${NOMBRE}\\s*=`).test(l));
  if (indice >= 0) {
    const valor = lineas[indice].slice(lineas[indice].indexOf('=') + 1).trim().replace(/^["']|["']$/g, '');
    if (valor.length >= LARGO_MINIMO) return { contenido: texto, estado: 'existente' };
    if (valor.length > 0) return { contenido: texto, estado: 'corta' };
    lineas[indice] = `${NOMBRE}=${generar()}`;
  } else {
    lineas.push(`${NOMBRE}=${generar()}`);
  }
  return { contenido: lineas.join(salto) + salto, estado: 'creada' };
}
