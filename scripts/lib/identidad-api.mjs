// Identidad para los scripts que hablan con la API como un ciudadano (ADR-090). Desde F2 quien reporta ya no inventa una
// `huella`: el servidor emite un token de dispositivo (POST /api/dispositivos) y se manda en la cabecera X-Dispositivo.
// Un voto cuenta para el quórum de un barrio solo si parte del sustento está verificado, y la verificación por ubicación pide
// una coordenada DENTRO del barrio con `precisionMetros` ≤ 200: de ahí `puntosPorBarrio`.
//
// Ojo con el tope: la API limita a 10 identidades por hora por IP. Para una siembra o una prueba que necesite muchas más, se
// levanta el backend con RATE_LIMIT_FACTOR (ver docker-compose.yml); sin eso, `crearDispositivo` se detiene con un error claro.

export async function crearDispositivo(api) {
  const respuesta = await fetch(`${api}/api/dispositivos`, { method: 'POST' });
  if (respuesta.status === 429) {
    const segundos = respuesta.headers.get('retry-after') ?? '?';
    throw new Error(
      `POST /api/dispositivos respondió 429: el backend solo emite 10 identidades por hora por IP (reintenta en ${segundos} s). `
        + 'Para sembrar o probar con más, levanta el backend con RATE_LIMIT_FACTOR=100 (docker compose up -d backend).',
    );
  }
  if (respuesta.status !== 201) {
    throw new Error(`POST /api/dispositivos respondió ${respuesta.status}: ${await respuesta.text()}`);
  }
  return (await respuesta.json()).token;
}

/** Cabeceras de un reporte de ciudadano con la identidad dada. */
export const conDispositivo = (token, extra = {}) => ({ 'Content-Type': 'application/json', 'X-Dispositivo': token, ...extra });

// --- Un punto dentro de cada barrio -------------------------------------------------------------------------------------------

function dentroDeAnillo([x, y], anillo) {
  let dentro = false;
  for (let i = 0, j = anillo.length - 1; i < anillo.length; j = i++) {
    const [xi, yi] = anillo[i];
    const [xj, yj] = anillo[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) dentro = !dentro;
  }
  return dentro;
}

const dentroDePoligono = (punto, [exterior, ...huecos]) =>
  dentroDeAnillo(punto, exterior) && !huecos.some((hueco) => dentroDeAnillo(punto, hueco));

/**
 * Un punto estrictamente dentro de la geometría (Polygon o MultiPolygon). El centro de la caja envolvente no sirve para barrios
 * cóncavos: se prueba primero y, si cae fuera, se recorre una cuadrícula cada vez más fina hasta dar con uno que sí esté dentro.
 */
export function puntoDentro(geometria) {
  const poligonos = geometria.type === 'MultiPolygon' ? geometria.coordinates : [geometria.coordinates];
  const puntos = poligonos.flatMap(([exterior]) => exterior);
  const xs = puntos.map((p) => p[0]);
  const ys = puntos.map((p) => p[1]);
  const [minX, maxX, minY, maxY] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
  const estaDentro = (p) => poligonos.some((poligono) => dentroDePoligono(p, poligono));

  const centro = [(minX + maxX) / 2, (minY + maxY) / 2];
  if (estaDentro(centro)) return { latitud: centro[1], longitud: centro[0] };
  for (let paso = 4; paso <= 256; paso *= 2) {
    for (let i = 1; i < paso; i++) {
      for (let j = 1; j < paso; j++) {
        const candidato = [minX + ((maxX - minX) * i) / paso, minY + ((maxY - minY) * j) / paso];
        if (estaDentro(candidato)) return { latitud: candidato[1], longitud: candidato[0] };
      }
    }
  }
  throw new Error('No se encontró un punto dentro de la geometría');
}

/** sectorId → una coordenada que cae dentro del barrio, leída de GET /api/sectores/geometria. */
export async function puntosPorBarrio(api) {
  const respuesta = await fetch(`${api}/api/sectores/geometria`);
  if (!respuesta.ok) throw new Error(`GET /api/sectores/geometria respondió ${respuesta.status}`);
  const { features } = await respuesta.json();
  return new Map(features.map((f) => [f.id, puntoDentro(f.geometry)]));
}
