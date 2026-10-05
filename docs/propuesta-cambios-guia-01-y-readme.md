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
