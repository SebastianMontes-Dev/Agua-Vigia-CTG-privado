# 01 · Guía paso a paso: levantar AguaVigía a mano y recorrer el backend

Objetivo: arrancar el sistema desde cero, servicio por servicio, explicando qué hace cada pieza, y luego recorrer todos los
flujos del backend con peticiones reales. Los comandos de esta guía se ejecutaron el 2026-09-29 (hora de Cartagena) en
**Windows PowerShell 5.1** contra un backend levantado de esta forma (salvo el visor web opcional de la parte E); tras cada bloque va lo que debe salir.

**Antes de empezar**
- Docker Desktop abierto. No hace falta Node, Java ni un `.env`.
- Usa **una sola ventana de PowerShell** de principio a fin: los bloques de las partes B y D reutilizan variables (`$api`, `$h`).
- En este PowerShell antiguo **no existe `&&`**: un comando por línea.
- Nunca pegues en un chat ni en un archivo la clave del ADMIN ni el secreto del segundo factor.

| Parte | Qué se hace |
|---|---|
| A | Levantar Mongo, réplica, Redis, Mailhog y backend |
| B | Primer ingreso del ADMIN con segundo factor (TOTP) |
| C | Sembrar los 211 barrios |
| D | Recorrido de flujos: consulta pública, reportes y consenso, suscripción, corte oficial, cuentas y roles, seguridad |
| E | Mirar la base de datos |
| F | Apagar, reiniciar y problemas frecuentes |

---

## Parte A · Levantar los servicios

### A0. Partir de cero

```powershell
cd C:\Users\sabas\Documentos\Agua-Vigia-CTG
```
```powershell
docker compose --profile demo --profile carga --profile siembra down -v
```

Borra contenedores **y volúmenes** (`mongo-data`, `redis-data`, `fotos-data`) solo de este proyecto. Los contenedores de otros
proyectos no se tocan. Con esto no queda ningún dato ni ninguna clave: se crea todo de nuevo.

### A1. MongoDB (la base de datos)

```powershell
docker compose up -d mongo
```
```powershell
docker exec aguavigia-mongo mongosh --quiet --eval "db.adminCommand('ping')"
```
Espera unos 15 s a que esté `healthy`. Resultado: `{ ok: 1 }`. Arranca con `--replSet rs0` pero la réplica aún no está iniciada
(`rs.status()` respondería `NotYetInitialized`).

### A2. Réplica de un solo nodo

```powershell
docker compose up mongo-init-replica
```
```powershell
docker exec aguavigia-mongo mongosh --quiet --eval "rs.status().members[0].stateStr"
```
Contenedor de un solo uso: ejecuta `rs.initiate`. **Por qué:** sin réplica Mongo no admite transacciones multi-documento y el backend las
necesita (por ejemplo, guardar un corte, su bitácora y el estado de sus barrios como una sola unidad). Salida: `Replica set iniciado` y, después, `PRIMARY`.

### A3. Redis y Mailhog

```powershell
docker compose up -d redis mailhog
```
```powershell
docker exec aguavigia-redis redis-cli ping
```
- **Redis** (`:6379`): caché, límites de peticiones, ventana de consenso, revocación de sesiones. Responde `PONG`.
- **Mailhog** (SMTP `:1025`, interfaz http://localhost:8025): captura todos los correos que envía el backend. **Nada sale a Internet.**
  Ábrelo en el navegador y déjalo a la vista durante la demostración.

### A4. Backend

```powershell
docker compose up -d --build backend
```
Compila la imagen (Docker multi-etapa) y arranca Spring Boot con el perfil `docker`. La primera vez tarda unos minutos.
La API queda en **http://localhost:8081**.

```powershell
curl.exe http://localhost:8081/actuator/health/readiness
```
Resultado: `{"status":"UP"}`.

Al arrancar con la colección `usuarios` vacía, el backend crea `admin@aguavigia.local` con una **clave aleatoria que se imprime una sola vez en el log**
(y un secreto de sesión aleatorio que nadie ve). Esta clave no está en ningún archivo del repositorio.

### A5. Preparar el sembrador (una vez)

```powershell
docker compose build sembrador
```
El sembrador es un contenedor con Node y los scripts de siembra. Sirve para dos cosas en esta guía: cargar los barrios (Parte C) y calcular
códigos del segundo factor sin instalar nada (Parte B). Está bajo el perfil `siembra`, así que `docker compose up` a secas nunca siembra por su cuenta.

---

## Parte B · Primer ingreso del ADMIN

Un ADMIN **debe** tener segundo factor. Por eso su primera sesión es restringida: solo sirve para dar de alta el TOTP.
Flujo: `sesión (alcance ALTA_SEGUNDO_FACTOR)` → `alta` → `código` → `confirmación` → `sesión completa`.

### B1. Leer la clave del log

```powershell
docker compose logs backend | findstr ADMINISTRADOR
```
Aparece una línea con la cuenta y la clave. Cópiala a tu gestor de contraseñas; el log no la vuelve a mostrar si el contenedor se recrea.

### B2. Iniciar sesión (sesión restringida)

```powershell
$api = "http://localhost:8081"
[string]$clave = Read-Host "Clave del ADMIN (la del log)"
$s1 = Invoke-RestMethod -Method Post -Uri "$api/api/veedor/sesion" -ContentType "application/json" -Body (@{ correo = "admin@aguavigia.local"; clave = $clave } | ConvertTo-Json)
$s1.alcance
```
Resultado: `ALTA_SEGUNDO_FACTOR`. Con ese token cualquier otra ruta del panel responde 403.

### B3. Dar de alta el segundo factor

```powershell
$h = @{ Authorization = "Bearer $($s1.token)" }
$alta = Invoke-RestMethod -Method Post -Uri "$api/api/veedor/segundo-factor/alta" -Headers $h -ContentType "application/json" -Body "{}"
$alta.secreto
```
Muestra el secreto en Base32 (también viene `$alta.uri`, la dirección `otpauth://` que se convierte en QR). **Solo se muestra esta vez.**
Guárdalo en tu gestor. Dos formas de obtener códigos de 6 dígitos:

- **App del teléfono** (Google Authenticator, Authy…): «Introducir clave de configuración», pega `$alta.secreto`, tipo basado en tiempo.
- **Calculadora del proyecto**, sin teléfono:
  ```powershell
  docker compose run --rm -T sembrador totp $alta.secreto 2>$null
  ```
  Imprime el código de 6 dígitos actual (cambia cada 30 s).

### B4. Confirmar y obtener la sesión completa

```powershell
[string]$codigo = Read-Host "Código de 6 dígitos"
$s2 = Invoke-RestMethod -Method Post -Uri "$api/api/veedor/segundo-factor/confirmacion" -Headers $h -ContentType "application/json" -Body (@{ codigo = $codigo } | ConvertTo-Json)
$s2.alcance
$h = @{ Authorization = "Bearer $($s2.token)" }
```
Resultado: `COMPLETO`. Desde aquí `$h` es tu sesión de ADMIN (vale 8 horas). Comprueba quién eres:

```powershell
Invoke-RestMethod "$api/api/veedor/yo" -Headers $h
```
Resultado: `rol ADMIN`, `segundoFactorActivo True` y siete permisos (`VER_PANEL`, `GESTIONAR_CORTES`, `MODERAR_REPORTES`, `GESTIONAR_USUARIOS`, `VER_AUDITORIA`, `REVISAR_INGESTA`, `CONFIGURAR_SEGUNDO_FACTOR`).

### B5. Volver a entrar más tarde (o si cierras la ventana)

Desde ahora el login exige el código. Un código no se puede usar dos veces en 2 minutos: si acabas de usar uno, espera a que cambie.

```powershell
[string]$clave = Read-Host "Clave del ADMIN"
[string]$codigo = Read-Host "Código de 6 dígitos"
$s = Invoke-RestMethod -Method Post -Uri "$api/api/veedor/sesion" -ContentType "application/json" -Body (@{ correo = "admin@aguavigia.local"; clave = $clave; codigoTotp = $codigo } | ConvertTo-Json)
$h = @{ Authorization = "Bearer $($s.token)" }
```
Si mandas la clave sin el código, la respuesta es `401 segundo-factor-requerido` (no es una clave mala: falta el código).

---

## Parte C · Los 211 barrios

La base arranca con 1 ADMIN y 0 barrios. Se siembran pidiéndole al sembrador **solo** ese paso:

```powershell
docker compose run --rm sembrador sembrar-sectores
```
```powershell
(Invoke-RestMethod "$api/api/sectores").sectores.Count
```
Resultado: `211` (el archivo geográfico trae 213 filas y el sembrador descarta 2 duplicadas). Cada barrio tiene polígono `2dsphere`, población y
`estado` vacío: **un barrio sin dato verificado se publica sin estado**, nunca como «con servicio» inventado.

> `docker compose --profile siembra up` correría además el paso `inicial` (30 000 cuentas de demostración, histórico de cortes y reportes).
> Para esta demostración no se usa: partimos de una base limpia.

---

## Parte D · Recorrido de flujos

### D1. Consulta pública (sin sesión)

```powershell
Invoke-RestMethod "$api/api/sectores/albornoz"
Invoke-RestMethod "$api/api/estadisticas"
Invoke-RestMethod "$api/api/bitacora"
```
`estado` es `null` y la bitácora está vacía: todavía no hay nada verificado. `GET /api/cumplimiento` responde 400 «No hay cortes cerrados todavía».

### D2. Reportes ciudadanos y consenso

Un vecino reporta sin registrarse. El sistema no cree a una sola persona: cambia el estado de un barrio cuando **varios reportes independientes**
coinciden. El umbral es proporcional a la población (0,1 %, mínimo 3): ALBORNOZ (853 hab.) necesita 3; MANGA (10 754 hab.) necesitaría 11.

```powershell
foreach ($i in 1..3) {
  $huella = [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")
  Invoke-RestMethod -Method Post -Uri "$api/api/reportes" -ContentType "application/json" -Body (@{ sectorId = "albornoz"; tipo = "SIN_AGUA"; huella = $huella } | ConvertTo-Json)
}
```
```powershell
Start-Sleep -Seconds 3
Invoke-RestMethod "$api/api/sectores/albornoz"
Invoke-RestMethod "$api/api/bitacora"
```
Resultado: `estado SIN_SERVICIO` y un evento `CORTE_CONFIRMADO_POR_CIUDADANOS` con `cantidadReportesSustento 3`. (El consenso se evalúa en un barrido de 1 s.)
La `huella` es un identificador anónimo del dispositivo (32–128 caracteres), no una cuenta.

**Cupo por dispositivo:** el mismo dispositivo solo puede reportar 3 veces por barrio cada 30 minutos.

```powershell
$huella = "a" * 40
1..4 | ForEach-Object { try { (Invoke-WebRequest -Method Post -Uri "$api/api/reportes" -UseBasicParsing -ContentType "application/json" -Body (@{ sectorId = "crespo"; tipo = "SIN_AGUA"; huella = $huella } | ConvertTo-Json)).StatusCode } catch { [int]$_.Exception.Response.StatusCode } }
```
Resultado: `201`, `201`, `201`, `429`.

### D3. Suscripción a avisos por correo (doble confirmación)

```powershell
Invoke-RestMethod -Method Post -Uri "$api/api/suscripciones" -ContentType "application/json" -Body (@{ correo = "vecino@ejemplo.com"; sectorIds = @("bocagrande") } | ConvertTo-Json)
```
Resultado: `estado PENDIENTE_CONFIRMACION`. Abre **http://localhost:8025**: llegó «Confirma que quieres recibir los avisos de BOCAGRANDE».
El enlace del correo apunta a la pantalla del frontend (puerto 5173); como aquí no corre, confirma con la API. Un GET a ese enlace solo muestra una página con
un botón (un antivirus o una vista previa no deben confirmar por accidente); la acción es un **POST**:

```powershell
$m = (Invoke-RestMethod "http://localhost:8025/api/v2/messages").items | Select-Object -First 1
$token = [regex]::Match(($m.Content.Body -replace "=\r?\n","" -replace "=3D","="), 'confirmar\?token=([0-9a-f-]{36})').Groups[1].Value
Invoke-RestMethod -Method Post -Uri "$api/api/suscripciones/confirmar?token=$token" -Headers @{ Accept = "application/json" }
```
Resultado: `estado CONFIRMADA`.

### D4. Corte oficial: registrar, avisar, cerrar y medir el cumplimiento

El veedor (ADMIN o VEEDOR) registra un corte anunciado. Este corte ya empezó hace una hora y se prometió por 4 h:

```powershell
$ini = (Get-Date).ToUniversalTime().AddHours(-1).ToString("yyyy-MM-ddTHH:mm:ssZ")
$fin = (Get-Date).ToUniversalTime().AddHours(3).ToString("yyyy-MM-ddTHH:mm:ssZ")
$corte = Invoke-RestMethod -Method Post -Uri "$api/api/veedor/cortes" -Headers $h -ContentType "application/json" -Body (@{ sectoresAfectados = @("bocagrande"); inicio = $ini; finPrometido = $fin; causa = "Reparacion de tuberia matriz" } | ConvertTo-Json)
$corte
```
Resultado: `estado ANUNCIADO`. Guardar el corte, su evento de bitácora y el estado de cada barrio afectado es **una sola transacción**.

```powershell
Start-Sleep -Seconds 3
(Invoke-RestMethod "$api/api/sectores/bocagrande").estado
```
Resultado: `SIN_SERVICIO` (si el corte empezara en el futuro sería `CORTE_PROGRAMADO`). En Mailhog llegó «Se fue el agua en BOCAGRANDE» al suscriptor confirmado.

Cerrar el corte con la hora real de restablecimiento (método **PATCH**):

```powershell
$hora = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
Invoke-RestMethod -Method Patch -Uri "$api/api/veedor/cortes/$($corte.id)/cierre" -Headers $h -ContentType "application/json" -Body (@{ horaReal = $hora } | ConvertTo-Json)
```
```powershell
Start-Sleep -Seconds 3
(Invoke-RestMethod "$api/api/sectores/bocagrande").estado
Invoke-RestMethod "$api/api/cumplimiento"
Invoke-RestMethod "$api/api/bitacora?sectorId=bocagrande"
```
Resultado: `CON_SERVICIO`; el **Índice de Cumplimiento** compara lo prometido (14 400 s) con lo real (≈ 3 600 s); la bitácora tiene `CORTE_ANUNCIADO` y
`CORTE_RESTABLECIDO`. El índice suma duraciones, no promedia porcentajes.

### D5. Cuentas y roles

Cualquiera puede pedir acceso al panel; nadie entra sin verificar el correo **y** sin que un ADMIN lo apruebe.

```powershell
$claveAna = "Clave-Ana-2026-Larga"
(Invoke-WebRequest -Method Post -Uri "$api/api/cuentas/registro" -UseBasicParsing -ContentType "application/json" -Body (@{ correo = "ana@ejemplo.com"; nombre = "Ana Prueba"; clave = $claveAna; barrioId = "bocagrande" } | ConvertTo-Json)).StatusCode
```
Resultado: `202` siempre, exista o no la cuenta (no se puede averiguar qué correos están registrados). En Mailhog llegó el correo de verificación con un enlace a **una página del
propio backend** (`/api/cuentas/enlaces/verificar?token=…`): puedes abrirla en el navegador y pulsar el botón, o hacerlo por API:

```powershell
$m = (Invoke-RestMethod "http://localhost:8025/api/v2/messages").items | Where-Object { $_.Content.Headers.To -contains "ana@ejemplo.com" } | Select-Object -First 1
$tok = [regex]::Match(($m.Content.Body -replace "=\r?\n","" -replace "=3D","="), 'token=([0-9A-Za-z_-]+)').Groups[1].Value
(Invoke-WebRequest -Method Post -Uri "$api/api/cuentas/verificacion?token=$tok" -UseBasicParsing).StatusCode
```
Resultado: `204`. Ana ya verificó el correo pero todavía no puede entrar:

```powershell
try { Invoke-RestMethod -Method Post -Uri "$api/api/veedor/sesion" -ContentType "application/json" -Body (@{ correo = "ana@ejemplo.com"; clave = $claveAna } | ConvertTo-Json) } catch { [int]$_.Exception.Response.StatusCode }
```
Resultado: `403`. El ADMIN ve la cola y la aprueba con un rol (método **PATCH**):

```powershell
$ana = (Invoke-RestMethod "$api/api/veedor/usuarios?estado=PENDIENTE_APROBACION" -Headers $h) | Where-Object { $_.correo -eq "ana@ejemplo.com" } | Select-Object -First 1
$ana.estado
Invoke-RestMethod -Method Patch -Uri "$api/api/veedor/usuarios/$($ana.id)/aprobacion" -Headers $h -ContentType "application/json" -Body (@{ rol = "OBSERVADOR" } | ConvertTo-Json)
```
Resultado: `PENDIENTE_APROBACION`, luego `estado ACTIVA rol OBSERVADOR`. Ana entra, pero con permisos mínimos:

```powershell
$sa = Invoke-RestMethod -Method Post -Uri "$api/api/veedor/sesion" -ContentType "application/json" -Body (@{ correo = "ana@ejemplo.com"; clave = $claveAna } | ConvertTo-Json)
$sa.rol
$sa.permisos -join ", "
$ha = @{ Authorization = "Bearer $($sa.token)" }
try { Invoke-RestMethod -Method Post -Uri "$api/api/veedor/cortes" -Headers $ha -ContentType "application/json" -Body (@{ sectoresAfectados = @("manga"); inicio = $ini; finPrometido = $fin; causa = "x" } | ConvertTo-Json) } catch { [int]$_.Exception.Response.StatusCode }
```
Resultado: `OBSERVADOR`, `CONFIGURAR_SEGUNDO_FACTOR, VER_PANEL`, y `403` al intentar crear un corte. Roles: **ADMIN** (todo), **VEEDOR** (cortes, moderación, ingesta),
**OBSERVADOR** (solo ver); un ADMIN además puede conceder o quitar permisos sueltos por persona. Cada acción queda en la auditoría:

```powershell
(Invoke-RestMethod "$api/api/veedor/auditoria" -Headers $h) | Select-Object -First 6 accion, detalle
```
(`CUENTA_REGISTRADA`, `CORREO_VERIFICADO`, `CUENTA_APROBADA`, `SESION_INICIADA`, `SESION_RECHAZADA`…)

### D6. Seguridad que se puede ver

**Rutas cerradas por defecto.** Lo que nadie declaró público responde 401, no 404:
```powershell
try { Invoke-RestMethod "$api/api/ruta-inventada" } catch { [int]$_.Exception.Response.StatusCode }
try { Invoke-RestMethod "$api/api/veedor/yo" } catch { [int]$_.Exception.Response.StatusCode }
```
Resultado: `401` y `401`.

**Bloqueo por cuenta y dirección.** Cinco claves malas seguidas bloquean 15 minutos a quien falló, sin dejar fuera a nadie más:
```powershell
1..6 | ForEach-Object { try { Invoke-RestMethod -Method Post -Uri "$api/api/veedor/sesion" -ContentType "application/json" -Body (@{ correo = "ana@ejemplo.com"; clave = "mala" } | ConvertTo-Json) | Out-Null } catch { [int]$_.Exception.Response.StatusCode } }
```
Resultado: `401` cinco veces y `423` (cuenta bloqueada). Ana queda bloqueada aunque escriba su clave correcta, pero el ADMIN sigue entrando sin problema.
Aparte hay un límite por IP de **10 intentos de login cada 5 minutos**.

**Cierre de sesión.** Revoca el token en Redis (espera 2 s: el margen de revocación es de un segundo):
```powershell
Start-Sleep -Seconds 2
Invoke-WebRequest -Method Post -Uri "$api/api/veedor/sesion/cierre" -Headers $ha -UseBasicParsing | Select-Object StatusCode
Start-Sleep -Seconds 2
try { Invoke-RestMethod "$api/api/veedor/yo" -Headers $ha } catch { [int]$_.Exception.Response.StatusCode }
```
Resultado: `204` y luego `401`.

**Puertos.** Mongo, Redis, Mailhog y la API solo escuchan en `127.0.0.1`: `docker compose ps` muestra `127.0.0.1:27017->27017`, etc. Nadie de la red del aula los alcanza.

---

## Parte E · Mirar la base de datos

```powershell
docker exec aguavigia-mongo mongosh aguavigia --quiet --eval "db.getCollectionNames().sort().forEach(c => print(c.padEnd(24), db.getCollection(c).countDocuments()))"
```
Colecciones: `usuarios`, `sectores`, `reportes`, `cortes`, `eventos_bitacora`, `suscripciones`, `tokens_cuenta`, `auditoria_cuentas`, `suscripciones_telegram`,
`propuestas_ingesta`, `documentos_fallidos`.

```powershell
docker exec aguavigia-mongo mongosh aguavigia --quiet --eval "db.usuarios.find({}, {claveHash:0, secretoTotp:0, _class:0}).forEach(u => print(u.correo, '|', u.rol, '|', u.estado))"
```
Las claves se guardan como hash BCrypt (`$2a$10$…`), nunca en claro. Índices que importan: `sectores` con `2dsphere` (¿a qué barrio pertenece este punto?) y `slug` único;
`usuarios.correo` único (una sola cuenta por correo); `reportes` con TTL de un año; `tokens_cuenta` con TTL (los enlaces vencidos se borran solos).

```powershell
docker exec aguavigia-mongo mongosh aguavigia --quiet --eval "db.usuarios.getIndexes().forEach(i => print(i.name))"
```

Visor web opcional: `docker compose --profile demo up -d mongo-express` y abrir http://localhost:8082.

---

## Parte F · Apagar, reiniciar y problemas frecuentes

- **Apagar conservando los datos:** `docker compose stop`. **Encender:** `docker compose start`. **Empezar de cero:** el `down -v` de A0.
- **Reiniciar solo el backend:** invalida las sesiones abiertas (el secreto de sesión es aleatorio en cada arranque) pero no los datos.

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `El token '&&' no es un separador…` | PowerShell antiguo | Un comando por línea |
| Login responde `429` | Más de 10 intentos de login en 5 min desde tu IP (detrás de Docker Desktop todos los clientes comparten IP) | Espera 5 min, o al ensayar: `docker compose exec redis redis-cli FLUSHALL` |
| Login responde `423` | Cuenta bloqueada por 5 fallos | Espera 15 min, o `FLUSHALL` como arriba |
| `401 segundo-factor-requerido` | Falta el código TOTP en el login | Añade `codigoTotp` (B5) |
| El código TOTP «no vale» | Ya se usó en los últimos 2 min, o caducó (30 s) | Espera el siguiente |
| Perdiste la clave o el TOTP del ADMIN | — | `node scripts/restablecer-admin.mjs --correo admin@aguavigia.local` (solo contra base local) o empezar de cero (A0) |
| El consenso no cambia el estado de un barrio | Barrio grande: el umbral es el 0,1 % de la población | Usa un barrio pequeño (ALBORNOZ) o más reportes |
| `Invoke-RestMethod` muestra un error rojo genérico | PowerShell oculta el cuerpo del error | Usa el patrón `try { … } catch { [int]$_.Exception.Response.StatusCode }` de esta guía |

**Otra trampa de PowerShell 5.1:** cuando la respuesta es un arreglo JSON, `Invoke-RestMethod` lo entrega como un solo objeto y no lo desenrolla en el pipeline; por eso los filtros llevan la llamada entre paréntesis: `(Invoke-RestMethod …) | Where-Object …`.

**Trampa de PowerShell 5.1:** si lees una clave con `Get-Content` y la metes en `ConvertTo-Json`, el texto lleva propiedades ocultas y el backend responde 400.
Por eso los ejemplos usan `Read-Host` o `[string]$variable`.
