# Propuesta de cambios a `docs/01-levantar-a-mano.md` y `docs/README.md`

Esos dos archivos son de Sebastian y están sin commitear, así que no se tocaron. Esta propuesta lista lo que F4 (ADR-094) los dejó
desactualizados. **Se borra cuando se apliquen.**

## `docs/01-levantar-a-mano.md`

### A0 (partir de cero)

El perfil `siembra` ya no existe en el compose (el `sembrador` no tiene perfil). El comando sigue funcionando (un perfil sin servicios no da
error), pero conviene quitarlo:

```powershell
docker compose --profile demo --profile carga down -v
```

### A5 (preparar el sembrador)

Reemplazar la última frase («Está bajo el perfil `siembra`, así que `docker compose up` a secas nunca siembra por su cuenta») por:

> Desde ADR-094 el sembrador **no está bajo ningún perfil**: un `docker compose up` a secas lo ejecuta al terminar de arrancar el backend, carga los
> 211 barrios y espera a que el backend termine de crear las cuentas sintéticas. En esta guía levantas los servicios uno a uno (`docker compose up -d
> backend` arranca el backend y lo que necesita, pero **no** el sembrador), así que los barrios los cargas tú en la Parte C.

### Parte A4 / arranque del backend (nota nueva)

> Con el perfil `docker`, el backend crea **30 000 cuentas sintéticas** de vecino en segundo plano al arrancar (`VECINOS_SINTETICOS`; con `0` no crea ninguna).
> Esperan a que existan el ADMIN inicial y los 211 barrios (hasta 5 minutos, reintentando cada 5 s), así que en esta guía empiezan **después de la Parte C**.
> Son lo único inventado del sistema: `GET /api/sistema/modo` las cuenta y `node scripts/verificar-datos.mjs` comprueba que son lo que dicen ser. Si
> no las quieres en tu base de práctica: `VECINOS_SINTETICOS=0` en el `.env` y `docker compose up -d --force-recreate backend`.

### Parte C (los 211 barrios)

- La frase «La base arranca con 1 ADMIN y 0 barrios» sigue siendo cierta en esta guía, pero **tras sembrar los barrios** aparecerán las 30 000 cuentas (ver arriba).
- Reemplazar la nota final («`docker compose --profile siembra up` correría además el paso `inicial` (30 000 cuentas de demostración, histórico de cortes y reportes)…»)
  por:

> `docker compose up` (sin perfil) hace de una vez lo que esta guía hace paso a paso: levanta todo, carga los 211 barrios y el backend crea las cuentas
> sintéticas. **No siembra reportes, cortes ni estados**: el mapa arranca en «sin datos» salvo los barrios con un boletín real de Acuacar vigente. Para comprobarlo:
> `docker compose run --rm sembrador verificar`.

### Ingesta (si la guía la menciona)

El modo por defecto del compose es ahora `auto`: Acuacar en vivo y, si falla, los 13 boletines reales guardados. `INGESTA_MODO=en-vivo` quita el respaldo; `local` no usa la red.

### Consenso (si la guía recorre un reporte)

El compose arranca con `AGUAVIGIA_CONSENSO_REDES_MINIMAS=1` y, para que un reporte cuente como verificado, hay que mandar la coordenada con `precisionMetros`. Un reporte anónimo
sin ubicación pesa en el conteo, pero el quórum pide que al menos un tercio del sustento esté verificado. `scripts/sembrar-demo.mjs` ya lo hace.

## `docs/README.md`

La fila de la guía 01 dice «Hecha y verificada (levantado, primer ingreso, flujos, base de datos)». Cuando se apliquen los cambios de arriba, conviene añadir a la
tabla una fila para el arranque único (puede apuntar a `07-decisiones-clave.md#adr-094`) y mantener la frase de abajo («Un dato vive en un solo archivo»): el detalle del
arranque vive en ADR-094 y en `docs/api/cambios-para-frontend.md` (sección F4), no se repite en el README.

## Compose: qué variables de `.env` llegan al backend (cierre de F4, B4)

- `docker-compose.yml` ya no usa `env_file: .env` ni en `mongo` ni en `backend`: todo lo de `.env` (por ejemplo `GITHUB_PERSONAL_ACCESS_TOKEN`) llegaba al contenedor.
  El backend recibe ahora una lista explícita: `ADMIN_INICIAL_CORREO`, `VEEDOR_PASSWORD_HASH`, `JWT_SECRET`, `IOT_KEY`, `TELEGRAM_BOT_TOKEN`, `INGESTA_INTERVALO_MS`,
  `INGESTA_RETRASO_INICIAL_MS`, `RATE_LIMIT_FACTOR_CUENTAS` y `AGUAVIGIA_MODO`, además de las que ya traía con valor por defecto.
- Van **sin valor** en el compose: si la variable está en `.env` llega igual; si no está, no existe en el contenedor y Spring aplica su valor por defecto.
  Una variable presente pero vacía (`JWT_SECRET=`) tapa ese valor por defecto, así que en `.env` se dejan comentadas las que no se usan (ya lo dice `.env.example`).
- Si la guía 01 dice que «el backend lee `.env` entero» o que basta con añadir cualquier variable a `.env`, corregirlo: una variable nueva que lea el backend hay que
  declararla también en el bloque `environment` del servicio `backend`.

## Puerto del visor de Mongo y perfil `simulacion` (F5)

- **`docs/01-levantar-a-mano.md` (línea ~363):** el visor web de Mongo (`docker compose --profile demo up -d mongo-express`) pasa de http://localhost:8082 a
  **http://localhost:8083**: el 8082 es ahora de la API de la instancia de simulación (`backend-sim`).
- **README / guía:** si se menciona la simulación, basta una línea: «`docker compose --profile simulacion up -d --build` levanta una segunda instancia (API en 8082, base
  `aguavigia_sim`) para ensayar el sistema con un guion acelerado; no toca la instancia real. Ver `scripts/simulacion/README.md`.» Los sensores IoT no forman parte de la simulación.

## Cierre de F5: la simulación

- **README (una línea más, junto a la de arriba):** «`node scripts/simulacion/preparar.mjs` genera la clave de la simulación en `.env`; luego
  `docker compose --profile simulacion run --rm simulador iniciar --velocidad 300` corre el día simulado completo (≈3 min) y comprueba 54 aserciones.»
- **`docs/01-levantar-a-mano.md`:** si la guía lista los puertos en uso, añadir **8082 = API de la simulación** (`backend-sim`, solo con `--profile simulacion`). Los comandos de
  `simulacion` necesitan Docker; no hay forma de correrla «a mano» sin él (necesita Mongo en réplica, Redis y Mailhog).
- **Aviso que conviene dejar escrito:** el Mailhog es el mismo de la instancia real. La simulación solo borra sus propios correos (`@sim.aguavigia.test`), pero llena el buzón: si se
  prueba la instancia real a la vez, sus correos se mezclan con los de la simulación.
- **Guías y README:** donde se hable de los sensores IoT, decir que están construidos pero **inactivos y fuera de la simulación**.

## Cierre de F6: las guardas de los scripts y la carga

- **`docs/01-levantar-a-mano.md`, Parte C (los 211 barrios):** `node scripts/sembrar-sectores.mjs` (y `sembrar-historico-cortes.mjs`, `preparar-pruebas-frontend.mjs`) ahora **se niegan** a correr si la base
  que reciben ya guarda reportes, cortes, propuestas o cuentas que no son de demostración, o si la URI no es local. Una base recién creada no se ve afectada. Para una base de pruebas que sí tiene esos
  datos: `--sobre-datos-reales`; para una base que no es local: `--permitir-remoto` (son dos permisos distintos). Conviene decirlo donde la guía invita a sembrar, porque quien repita el paso sobre la base
  de uso diario verá un rechazo en vez de perder los estados de los barrios.
- **README:** una línea con el ADR nuevo, «ADR-097: cierre de F6 (carga medida, métricas de calibración y revisiones de seguridad y de dominio)», y, donde se hable de la prueba de carga, que se mide en un
  proyecto Docker aparte y que sus cifras son de un PC compartido (ver ADR-097 §1).
- **Aviso:** `docs/07-decisiones-clave.md` ADR-096 aceptaba que la simulación compartiera el canal SSE de Redis con la instancia real; ADR-097 lo corrige (el canal lleva sufijo por modo).
