# Flujos de usuario

Qué puede hacer cada tipo de persona y **en qué orden llama a la API**. Cada flujo enlaza a la guía que
detalla las rutas. Es el mapa para decidir qué pantallas necesita el frontend.

Hay cuatro actores. Los dos primeros no tienen cuenta; el panel exige sesión.

| Actor | Cuenta | Qué hace |
|---|---|---|
| Ciudadano anónimo | No | Mira el mapa, reporta, confirma reportes, lee bitácora y estadísticas. |
| Ciudadano suscrito | No (solo un correo) | Recibe alertas por correo de los sectores que eligió. |
| Veedor / observador | Sí | Modera reportes, registra y cierra cortes, revisa lo que detectó la ingesta. |
| Administrador | Sí | Todo lo del veedor, más gestionar cuentas y ver la auditoría. |

---

## 1. Ciudadano anónimo — «¿tengo agua?»

Es el flujo principal: **debe responderse en menos de 5 segundos, en un celular, sin registro.**

```
1. GET  /api/sectores/geometria        (una vez; cacheable un día)  → polígonos para dibujar el mapa
2. GET  /api/sectores                  → estado actual de cada sector
3. GET  /api/sectores/stream  (SSE)    → avisa cuándo cambió algo; al recibir el aviso, repetir el paso 2
4. GET  /api/sectores/{id}             → ficha de un sector al tocarlo
```

- Une el paso 1 con el 2 por el `id` (el `id` de cada *Feature* del GeoJSON es el mismo del listado).
- Un sector con `estado: null` se pinta como **«sin datos»**, no como «con servicio».
- Detalle: [Sectores y tiempo real](sectores-y-tiempo-real.md).

### Reportar que no hay agua

```
1. (una vez por dispositivo) generar y guardar una huella anónima de 32 a 128 caracteres
2. POST /api/reportes                  { sectorId | coordenada, tipo, huella }   → 201
3. (opcional) POST /api/reportes/{id}/foto   multipart, parte "foto"             → 200
```

- Basta con el `sectorId`, **o** con la `coordenada` (el servidor infiere el barrio, RF007). Si el usuario
  concede la ubicación, no hace falta que elija sector a mano.
- Reportar cuesta como mucho dos toques (RF005). No hay registro ni captcha.
- Un mismo dispositivo puede reportar **3 veces por sector cada 30 minutos**; a la cuarta, `429`.
- Detalle: [Reportes ciudadanos](reportes.md).

### Confirmar el reporte de un vecino

```
POST /api/reportes/{id}/confirmar     { huella }    → 200
```

Las confirmaciones **no** cuentan para el consenso: son señal social, no evidencia que cambie el estado.

### Leer la historia

```
GET /api/bitacora?pagina=0&tamano=20        → eventos, más recientes primero (paginado por cabeceras)
GET /api/estadisticas                       → sectores más afectados, cortes por día, duración media
GET /api/estadisticas/exportar.csv          → lo mismo, descargable
GET /api/cumplimiento                       → Índice de Cumplimiento global
GET /api/cumplimiento/sectores/{id}         → el de un sector
GET /api/cumplimiento/serie?sectorId&desde&hasta   → evolución mensual
```

Detalle: [Bitácora, estadísticas y cumplimiento](bitacora-estadisticas-cumplimiento.md).

---

## 2. Ciudadano suscrito — alertas por correo

```
1. POST /api/suscripciones             { correo, sectorIds[] }   → 201, estado PENDIENTE_CONFIRMACION
2. (el usuario abre el enlace del correo)  GET /api/suscripciones/confirmar?token=…
3. (cada cambio de estado del sector)      el backend envía un correo con enlace de baja
4. (baja)  GET /api/suscripciones/cancelar?token=…
```

Doble confirmación (*doble opt-in*): hasta que el usuario confirme, no se le envía nada más. **Todo
correo lleva un enlace de baja en un clic**, y la baja elimina el correo del registro. Los enlaces del
correo son rutas del propio backend que responden una página HTML sencilla; un frontend propio puede
sustituirlas (ver [Correos y enlaces](correos-y-enlaces.md)).

Detalle: [Suscripciones](suscripciones.md).

---

## 3. Veedor — moderar y registrar

Requiere iniciar sesión. Lo que puede hacer depende de su rol y de sus permisos.

```
1. POST /api/veedor/sesion             { correo, clave, codigoTotp? }   → { token, permisos[], alcance, … }
2. (con el token) GET /api/veedor/yo   → quién soy, qué puedo hacer (al recargar la página)
```

Con permiso **`VER_PANEL`** (todo veedor u observador):

```
GET /api/veedor/reportes/pendientes         → cola de moderación (los más antiguos primero)
GET /api/veedor/ingesta/propuestas          → lo que detectó la ingesta y espera revisión
GET /api/veedor/ingesta/salud               → estado de cada colector de fuentes
GET /api/veedor/cortes?sectorId=…           → cortes de un sector
```

Con permiso **`MODERAR_REPORTES`** (veedor):

```
PATCH /api/veedor/reportes/{id}/aprobar
PATCH /api/veedor/reportes/{id}/descartar
```

Con permiso **`GESTIONAR_CORTES`** (veedor):

```
POST  /api/veedor/cortes                    { sectoresAfectados[], inicio, finPrometido, causa }
PATCH /api/veedor/cortes/{id}/cierre        { horaReal }
```

Con permiso **`REVISAR_INGESTA`** (veedor):

```
PATCH /api/veedor/ingesta/propuestas/{id}/aprobar
PATCH /api/veedor/ingesta/propuestas/{id}/descartar
```

La interfaz debe **pintar solo lo que `permisos[]` permite**: el servidor rechaza con `403` lo demás, pero
mostrar un botón que va a fallar es mala experiencia. Detalle: [Panel del veedor](panel-veedor.md).

---

## 4. Administrador — cuentas y auditoría

Un ADMIN hace todo lo anterior, más:

```
GET   /api/veedor/usuarios?estado=…                       → lista de cuentas (paginada)
POST  /api/veedor/usuarios/invitaciones                   { correo, nombre, rol }
PATCH /api/veedor/usuarios/{id}/aprobacion                { rol, concedidos[], revocados[] }
PATCH /api/veedor/usuarios/{id}/rechazo | suspension | reactivacion
PATCH /api/veedor/usuarios/{id}/permisos                  { rol, concedidos[], revocados[] }
GET   /api/veedor/auditoria                               → quién hizo qué sobre las cuentas
```

### Primer ingreso de un ADMIN

Un ADMIN **debe** dar de alta el segundo factor (TOTP) antes de poder hacer nada más. Su primer login
devuelve un token con `alcance: "ALTA_SEGUNDO_FACTOR"`, que solo sirve para:

```
1. POST /api/veedor/segundo-factor/alta            → { uri, secreto }   (mostrar el QR; el secreto se ve UNA vez)
2. POST /api/veedor/segundo-factor/confirmacion    { codigo }           → sesión nueva con alcance COMPLETO
```

Detalle completo, con los estados de una cuenta, en [Cuentas y sesión](cuentas-y-sesion.md).

---

## 5. Cómo se crea una cuenta

Hay dos caminos, y el frontend necesita pantallas para ambos:

| Camino | Pasos |
|---|---|
| **Registro abierto** | `POST /api/cuentas/registro` → correo con enlace → verificar → un ADMIN aprueba → `ACTIVA` |
| **Invitación** | Un ADMIN invita → correo con enlace → la persona fija su clave → `ACTIVA` (sin aprobación extra) |

Recuperar la clave: `POST /api/cuentas/restablecimiento` (responde siempre `202`, exista o no el correo)
→ correo → `POST /api/cuentas/clave`.

---

## Lo que ninguna pantalla debe olvidar

- **Frescura visible.** Mostrar *cuándo* se registró el estado (`actualizadoEn`) y, si el dato es viejo,
  decirlo. Un dato sin fecha no vale.
- **Estados de carga, vacío y error** en cada lista.
- **El color nunca va solo.** Los cuatro estados del servicio necesitan además forma o texto. Ver
  [`DESIGN.md`](../../DESIGN.md).
- **Reaccionar por `type`, no por texto**, ante los errores 401 (ver [Errores y límites](errores-y-limites.md)).
