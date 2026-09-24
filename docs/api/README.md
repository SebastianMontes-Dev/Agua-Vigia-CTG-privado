# API de AguaVigía CTG — guía para construir el frontend

> **Para quién es.** Para quien vaya a construir el frontend desde cero. El frontend anterior se
> retiró del repositorio (`ADR-048`; su código sigue en la etiqueta git `pre-retiro-frontend`), así que
> **nada de lo que está aquí presupone una interfaz concreta**: describe qué hace el backend, qué rutas
> expone y qué reglas debe respetar cualquier cliente.
>
> **Fuente de verdad.** El contrato exacto (campos, tipos, códigos) es
> [`backend/openapi.yaml`](../../backend/openapi.yaml), generado desde el código y comprobado en cada
> build (`ContratoOpenApiTest`). Estas guías explican lo que el contrato no puede: el *porqué*, los
> flujos, las reglas de negocio y las trampas. Si una guía y el contrato discrepan, gana el contrato.

---

## Qué es AguaVigía CTG

Plataforma ciudadana e independiente de monitoreo del acueducto de **Cartagena de Indias**. Cruza los
avisos oficiales de Acuacar con reportes ciudadanos georreferenciados y publica un **Índice de
Cumplimiento**: la duración *prometida* de cada corte frente a la *real*. No es de Acuacar ni de ningún
ente distrital.

**El problema que resuelve es informativo, no hidráulico.** La pregunta que la interfaz tiene que
responder en menos de cinco segundos, desde un celular y sin registro, es: **«¿tengo agua o no, y hasta
cuándo?»**

## Índice

| Guía | Contenido |
|---|---|
| [Flujos de usuario](flujos-de-usuario.md) | Qué hace cada tipo de usuario y en qué orden llama a la API. **Empieza aquí.** |
| [Sectores y tiempo real](sectores-y-tiempo-real.md) | El mapa: listado, polígonos, estado, SSE. |
| [Reportes ciudadanos](reportes.md) | Reportar, foto, confirmar, consenso y cupos. |
| [Suscripciones por correo](suscripciones.md) | Alertas por sector con doble confirmación. |
| [Bitácora, estadísticas y cumplimiento](bitacora-estadisticas-cumplimiento.md) | Historia pública y el Índice de Cumplimiento. |
| [Cuentas y sesión](cuentas-y-sesion.md) | Registro, invitaciones, login, segundo factor, permisos. |
| [Panel del veedor](panel-veedor.md) | Cortes, moderación, ingesta, cuentas y auditoría. |
| [Errores y límites](errores-y-limites.md) | Formato de error, códigos, `Retry-After`, paginación, CORS. |
| [Correos y enlaces](correos-y-enlaces.md) | Qué correos salen y a qué rutas apuntan sus enlaces. |
| [Datos de referencia](datos-de-referencia.md) | Estados, tipos, roles, permisos y reglas de dominio. |
| [Consumir la API a escala](escalabilidad-para-el-cliente.md) | Cómo pedir datos sin tumbar el servicio (50 000 usuarios). |
| [Referencia de rutas](referencia-de-rutas.md) | Todas las rutas y todos los esquemas, **generada** desde el contrato. |

## Convenciones globales

**Base y formato.** Todo cuelga de `/api/…` (y `/fotos/…` para imágenes). JSON en UTF-8, salvo las tres
excepciones: el SSE (`text/event-stream`), los CSV y el GeoJSON (`application/geo+json`).

**Fechas.** ISO-8601 en UTC (`2026-08-08T15:30:00Z`). El servidor las guarda y responde en UTC; **la
presentación es en hora de Cartagena (UTC-5, sin horario de verano)**. Los agrupados por mes del Índice
de Cumplimiento (`"2026-08"`) ya se calculan en hora de Cartagena.

**Identificadores.** Cadenas opacas. El `id` de un sector es un *slug* estable (`bocagrande`,
`zona-industrial`); los demás son UUID.

**Valores nulos.** Un `null` significa «no hay dato», y **no es lo mismo que un valor por defecto**. El
caso crítico: el `estado` de un sector es `null` mientras nadie haya verificado nada, y la interfaz debe
mostrarlo como *«sin datos»*, **nunca** como «con servicio». Afirmar servicio normal sin verificarlo es
el falso positivo que el proyecto existe para evitar.

**Autenticación.** Solo `/api/veedor/**` (el panel) exige sesión, con `Authorization: Bearer <token>`.
Todo lo demás es público. Ver [Cuentas y sesión](cuentas-y-sesion.md).

**Errores.** Siempre RFC 7807 (`application/problem+json`). Ver [Errores y límites](errores-y-limites.md).

**Idioma.** Los textos de error, los `title` y las descripciones están en español; los identificadores
de dominio (`SIN_SERVICIO`, `PRESION_BAJA`) son constantes estables que el cliente traduce a su copy.

## Cómo levantar el backend para desarrollar

```bash
cp .env.example .env          # y completar JWT_SECRET, VEEDOR_PASSWORD_HASH, ADMIN_INICIAL_CORREO
docker compose up -d --build --wait
```

- API en `http://localhost:8081` (el compose de desarrollo publica el backend directo).
- Documentación interactiva: `http://localhost:8081/swagger-ui.html`.
- Correo de pruebas (Mailhog): `http://localhost:8025`; todo correo que envíe el backend aparece ahí.
- Datos: `node scripts/sembrar-sectores.mjs` carga los 211 sectores;
  `scripts/sembrar-demo.mjs` y `scripts/sembrar-historico-cortes.mjs` cargan datos de ejemplo.
  Detalle en [`docs/ingenieria/entorno-local.md`](../ingenieria/entorno-local.md).

## Qué debe saber ya quien construya la interfaz

1. **CORS está abierto en local y cerrado en producción.** Con `docker compose up` (perfil `docker`) ya pasan
   `localhost:5173`, `:3000` y `:4200`; otro origen se declara en `CORS_ORIGENES` del `.env`. En producción va
   todo detrás del mismo proxy. Ver
   [Errores y límites §CORS](errores-y-limites.md#cors).
2. **No hay endpoint de refresco de sesión.** El token del panel dura 8 horas; al caducar hay que volver
   a iniciar sesión.
3. **Los polígonos vienen de `GET /api/sectores/geometria`.** Ya no hace falta llevar el GeoJSON en el
   cliente ni calcular el `id`.
4. **El SSE solo avisa, no envía datos.** Al recibir el aviso, el cliente pide `GET /api/sectores`.
   Ver [Sectores y tiempo real](sectores-y-tiempo-real.md).
5. **Los reportes ciudadanos no llevan cuenta.** El cliente genera una *huella* anónima una vez y la
   reutiliza. Ver [Reportes](reportes.md).
