// ¿La URI de Mongo apunta a esta máquina o al servicio `mongo` de docker-compose? Se analiza la URI en vez de comprobar su
// inicio con una expresión regular: `mongodb://mongo:x@servidor-ajeno/` empieza igual que la local, pero el host real
// (lo que va después del `@`) es otro. Una URI con usuario y clave tampoco cuenta como local: las bases de demostración no las llevan.
export function esBaseLocal(uri, { permitirServicioMongo = true } = {}) {
  try {
    const url = new URL(uri);
    if (url.username || url.password) return false;
    const locales = ['localhost', '127.0.0.1', '[::1]'];
    if (permitirServicioMongo) locales.push('mongo');
    return locales.includes(url.hostname);
  } catch {
    return false;
  }
}
