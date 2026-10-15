# 06 · Ética de datos

Estado a 2026-10-15 (hora de Cartagena), contrastado con el código de `main`. AguaVigía publica información sobre el agua de una ciudad
y la toma de fuentes ajenas y de vecinos: estas reglas son **no negociables** (`CLAUDE.md`) y aquí se explica cómo se cumple cada una y dónde.

## 1. Las reglas y cómo se cumplen

| Regla | Cómo se cumple | Dónde |
|---|---|---|
| **Se respeta `robots.txt`**, aunque sea evadible (ADR-005) | Cada fuente se verifica **antes** de añadirla, con una petición real a su `robots.txt` y al recurso (skill `verificar-fuente`). Acuacar solo excluye `/wp-admin/`, y el colector solo lee `/wp-json/wp/v2/posts`. Agregar un medio de prensa es una fila de configuración, y solo se agrega uno ya verificado | `AcuacarApiCollector`, `application.yml` (`aguavigia.ingesta.rss`) |
| **No se disfraza el `User-Agent`** y el colector se identifica con nombre y correo de contacto | Los colectores de Acuacar y prensa **se niegan a hacer la petición** si `COLLECTOR_USER_AGENT` está vacío. El compose trae un valor por defecto con el nombre del proyecto y un correo | `AcuacarApiCollector`, `RssCollector`, `docker-compose.yml` |
| **No se scrapea Facebook, Instagram ni X** | Las únicas fuentes son la API REST oficial de Acuacar (no su HTML, ADR-004) y cuatro feeds RSS de prensa (Google News, Zona Cero, Caracol Radio y W Radio) | `application.yml` |
| **Nada llega al mapa sin verificación**: sin la frase exacta del boletín que la respalde, no se publica | Toda propuesta de la ingesta guarda su `citaTextual`; sin ella la compuerta la rechaza. Lo de prensa solo **propone** y lo publica un veedor (ADR-028). Lo de Acuacar se publica solo si pasa las compuertas de fiabilidad (confianza, duración, fechas y número de barrios), y si no, va a la cola del veedor con el motivo (ADR-092). Los boletines pasados entran como historia, sin cambiar el estado de ningún barrio | `CompuertaDePublicacion`, `RevisarPropuestaIngestaService`, `RegistrarPropuestaIngestaService` |
| **Antes de afirmar que una fuente está bloqueada o disponible, se prueba** | Skill `verificar-fuente` y agente `explorador-fuentes`: leen `robots.txt`, piden el recurso y emiten un veredicto con la respuesta real | `.claude/skills/verificar-fuente`, `.claude/agents/explorador-fuentes.md` |

## 2. Datos de las personas

- **Reportes sin registro** (ADR-007): no se pide nombre ni correo para reportar. La identidad es un token de dispositivo que emite el servidor; la
  base guarda solo su SHA-256, y caduca a los 365 días sin uso (ADR-090).
- **Ubicación mínima:**
  - La coordenada de un reporte se guarda redondeada a 3 decimales (≈ 110 m), nunca la exacta.
  - La que un vecino usa para verificar su barrio se usa y se descarta: solo queda la marca `barrioVerificado` (ADR-090).
- **Retención:** los reportes se borran solos al año (TTL), igual que los dispositivos sin uso, los enlaces de cuenta vencidos y la auditoría de los
  vecinos ([03 · Base de datos](03-base-de-datos.md)). Las fotos de evidencia se sirven solo si el reporte fue aprobado (ADR-091); su borrado a los
  180 días (D19) está construido pero **desactivado**, porque borrar evidencia es irreversible (ADR-097 §3).
- **La red de quien reporta** se guarda como hash (`redHash`), no como IP, y solo para exigir que el sustento venga de redes distintas.
- **Cuentas sintéticas:** las 30 000 de vecino son datos inventados y se declaran como tales (`datosDeDemostracion`, `GET /api/sistema/modo`); no
  tienen titular ni clave conocida (ADR-094, ADR-095).

## 3. Pendiente o mejorable

- **`robots.txt` no se vuelve a consultar en cada ciclo.** Se respeta por proceso (se verifica al añadir la fuente), no por código: si una fuente
  cambiara su `robots.txt`, el colector no lo notaría hasta la siguiente verificación manual. Una comprobación periódica sería la mejora natural.
- **El correo de contacto del `User-Agent` por defecto** (`alertas@aguavigia.com`) debe ser uno que controles: cámbialo con `COLLECTOR_USER_AGENT`
  en tu `.env` (lo avisa el propio `docker-compose.yml`).
- **Licencia del mapa de barrios:** el dataset de Cartagena Cómo Vamos es público, pero no declara licencia; hay que confirmar sus términos antes de
  publicarlo fuera del entorno local ([`data/geoespacial/README.md`](../data/geoespacial/README.md)).
