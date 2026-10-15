# 03 · Base de datos

Estado a 2026-10-15 (hora de Cartagena). **MongoDB es la base principal y la única fuente de verdad del sistema.** Redis guarda solo
estado efímero: si se vacía, se pierde la caché, los límites y la ventana de consenso, pero no un solo dato del negocio (ADR-003).

La fuente de verdad del esquema es el código: cada colección es una clase `*Documento` en
`backend/src/main/java/com/aguavigia/ctg/infrastructure/persistence/mongo/`, y sus índices se crean al arrancar en `IndicesMongo.java`.
Este documento explica qué guarda cada una y por qué; si algo de aquí contradice esas clases, ganan las clases.

## 1. MongoDB

### 1.1 Instalación

- **MongoDB 7** en un *replica set* de **un solo nodo** (`--replSet rs0`, iniciado por el contenedor `mongo-init-replica`). No hay copia en otro
  nodo: el modo réplica existe porque Mongo solo admite **transacciones multi-documento** así, y el backend las usa (guardar un corte, su
  bitácora y el estado de sus barrios como una sola unidad). No da tolerancia a fallos.
- Base `aguavigia` (instancia real) y `aguavigia_sim` (instancia de simulación, solo con `--profile simulacion`). Escucha solo en `127.0.0.1:27017`
  y **sin autenticación**: aceptable solo porque no sale del equipo (ver [05](05-seguridad-y-claves.md) §3).
- Los identificadores son UUID en texto, salvo `sectores`, que usa `ObjectId` y se busca por `slug`. Las referencias entre colecciones
  (`sectorId`, `corteId`, `usuarioId`, `reporteId`) son cadenas **sin integridad referencial**: la mantiene la aplicación.

### 1.2 Colecciones

| Colección | Qué guarda | Campos clave | Retención |
|---|---|---|---|
| `sectores` | Los 211 barrios del catastro con su polígono y su **estado vigente** | `slug` (único), `nombre`, `poblacion`, `localidad`, `zona`, `geometry` (GeoJSON), `estadoActual`, `estadoOrigen`, `estadoVerificadoEn`, `ventanaPrometidaInicio/Fin`, `respaldoVecinos`, `umbralVecinos`, `porConfirmar`, `enDisputa` | Permanente |
| `cortes` | Cortes oficiales y su cierre **barrio por barrio** (ADR-088) | `sectoresAfectados[]`, `inicio`, `finPrometido`, `finReal`, `causa`, `origen`, `estado`, `cierres[]` (`sectorId`, `hora`, `fuente`), `provisional`, `motivoAnulacion`, `caducaEn` | Permanente |
| `reportes` | Reportes ciudadanos | `sectorId`, `tipo`, `latitud`/`longitud` (redondeadas a 3 decimales, ≈ 110 m; ADR-090), `timestamp`, `estadoModeracion`, `verificacion`, `redHash`, `huella` (id del dispositivo emitido por el servidor), `fotoUrl`, `fotoSha256`, `esSensor` | **TTL 365 días** |
| `eventos_bitacora` | Historia pública: cada cambio de estado y su sustento | `tipo`, `sectorId`, `corteId`, `timestamp`, `descripcion`, `estado`, `fuente`, `urlOriginal`, `respaldoVecinos`, `respaldoUmbral` | Permanente |
| `propuestas_ingesta` | Lo que la ingesta extrajo de Acuacar y la prensa, con la **frase exacta** que lo respalda | `sectorId`, `estadoPropuesto`, `fuente`, `urlOriginal`, `citaTextual`, `tituloOriginal`, `detectadaEn`, `estadoRevision`, `inicioDeclarado`, `finPrometido`, `publicadoEn` | Permanente |
| `documentos_fallidos` | Documentos que la ingesta no pudo procesar, para reintentarlos | `hash`, `fuente`, `urlOriginal`, `motivo`, `primerIntento`, `ultimoIntento` | Permanente |
| `marcas_ingesta` | Hasta dónde llegó cada fuente | `fuente`, `ultimoPublicadoEn` | Permanente |
| `usuarios` | Cuentas del panel (ADMIN, VEEDOR, OBSERVADOR) y de vecinos | `correo` (único), `nombre`, `claveHash` (BCrypt), `rol`, `estado`, `permisosConcedidos/Revocados`, `secretoTotp`, `barrio`, `barrioVerificado`, `consentimientos[]`, `datosDeDemostracion` | Permanente |
| `tokens_cuenta` | Enlaces de verificar, invitar y restablecer (solo el hash del token) | `hash`, `tipo`, `usuarioId`, `usadoEn`, `expiraEn` | **TTL** al vencer |
| `auditoria_cuentas` | Quién hizo qué con las cuentas | `accion`, `autorId`, `sujetoId`, `detalle`, `ip`, `ocurrioEn`, `venceEn` | **TTL** según `venceEn` |
| `bloqueos_administracion` | Candado para que dos administradores no se pisen | `token`, `expiraEn` | Corta |
| `dispositivos` | Identidades de dispositivo emitidas (`POST /api/dispositivos`), guardadas como SHA-256 | `creadoEn`, `ultimoVisto` | **TTL 365 días** sin uso |
| `subidas_foto` | Tokens de un solo uso para subir la foto de un reporte | `hashDelToken`, `reporteId`, `venceEn` | **TTL** al vencer |
| `suscripciones` | Avisos por correo con doble confirmación | `correo`, `sectorIds[]` (máx. 211), `estado`, `tokenConfirmacion` (único) | Permanente |
| `suscripciones_telegram` | Avisos por Telegram | `sectorIds[]`, `creadaEn` | Permanente |
| `config_sistema` | Secretos que el backend genera y deben sobrevivir a un reinicio | `valor` | Permanente |

Las 30 000 cuentas sintéticas de vecino (ADR-094) viven en `usuarios` con `datosDeDemostracion: true` y ocupan casi todo el volumen de
la base; nadie conoce su clave. El resto de colecciones es pequeño: decenas o cientos de documentos en uso normal.

### 1.3 Índices

| Colección | Índices (además de `_id`) | Para qué |
|---|---|---|
| `sectores` | `geometry` **2dsphere**, `slug` único | ¿A qué barrio pertenece este punto?; búsqueda por nombre |
| `cortes` | `sectoresAfectados`, `finReal`, `estado` | Cortes de un barrio, cortes cerrados, vigentes |
| `reportes` | `sectorId+timestamp`, `sectorId+huella+timestamp`, `estadoModeracion+timestamp`, `fotoUrl` (*sparse*), `timestamp` **TTL** | Consenso por ventana, cupo por dispositivo, cola de moderación |
| `eventos_bitacora` | `timestamp`, `sectorId+timestamp`, `tipo+timestamp` | Bitácora paginada y filtrada |
| `propuestas_ingesta` | `estadoRevision+detectadaEn`, `sectorId+estadoPropuesto+estadoRevision`, `sectorId+urlOriginal`, `estadoRevision+finPrometido` | Cola del veedor, deduplicación, expiración |
| `usuarios` | `correo` único, `rol+estado`, `estado`, `barrio+creadoEn` | Inicio de sesión, gestión de cuentas |
| `tokens_cuenta` | `usuarioId`, `expiraEn` **TTL** | Enlaces de una cuenta; borrado al vencer |
| `auditoria_cuentas` | `ocurrioEn`, `venceEn` **TTL** | Consulta por fecha; caducidad |
| `suscripciones` | `tokenConfirmacion` único, `sectorIds` | Confirmar; a quién avisar |
| `suscripciones_telegram` | `sectorIds` | A quién avisar |
| `dispositivos` | `ultimoVisto` **TTL** | Caducidad por inactividad |
| `subidas_foto` | `venceEn` **TTL** | Caducidad del token |
| `documentos_fallidos` | `ultimoIntento` | Reintentos |

### 1.4 Transacciones y escrituras atómicas

- **Transacción multi-documento** (`TransaccionPort`, con reintento ante `TransientTransactionError`): registrar, cerrar o anular un corte junto con
  su bitácora y el estado de sus barrios.
- **Escrituras atómicas de un solo documento**: confirmar un reporte (`agregarConfirmacionSiVigente`), cambiar su moderación, marcar un enlace de
  cuenta como usado (dos peticiones con el mismo enlace: gana una).
- **Un solo escritor del estado de un barrio** (ADR-087): solo el resolutor cambia `sectores.estadoActual`; lo vigila una regla ArchUnit.

### 1.5 Copias de seguridad

`scripts/backup-mongo.sh` y `scripts/restore-mongo.sh` (con `mongodump`/`mongorestore`), y `backup-fotos.sh`/`restore-fotos.sh` para el volumen de
fotos. Antes de una prueba de carga o de un `down -v`, haz una copia.

## 2. Redis

Solo estado efímero; ninguna clave es dato del negocio.

| Prefijo de clave | Para qué |
|---|---|
| `rate-limit:` | Límite de peticiones por IP y ruta (ADR-018) |
| `cupo:` | Cupo de reportes por dispositivo o cuenta en la ventana |
| `consenso:sector:`, `aguavigia:consenso:reserva:`, `aguavigia:consenso:pendientes` | Ventana de consenso y evaluación pendiente por barrio |
| `login:fallos:`, `login:bloqueo:` | Bloqueo por cuenta y dirección |
| `sesion:revocada:` | Sesiones cerradas antes de vencer (si Redis cae, se deniega) |
| `ingesta:visto:` | Deduplicación reciente de la ingesta |
| `tarea-unica:`, `unico:` | Que una tarea programada corra en un solo proceso a la vez |
| `aguavigia:sse:sectores` (canal pub/sub, con sufijo por modo) | Avisar a todas las conexiones SSE de que un barrio cambió |

La caché de lectura de sectores también vive en Redis; si Redis falla, el backend lee de Mongo (`ManejadorDeErroresDeCache`).

## 3. Modelo entregado en Base de Datos 2

`modelo-nosql-moon-modeler.dmm` es el diagrama de la entrega de BD2 (transformación de un ER de 14 tablas a 10 colecciones, 2026-09-24).
**Es anterior al modelo actual**: no incluye `suscripciones_telegram`, `bloqueos_administracion`, `documentos_fallidos`, `dispositivos`,
`subidas_foto` ni `marcas_ingesta`, ni los campos de estado de barrio de F1–F4. Se conserva como entregable; el modelo vigente es el de §1.

## 4. PostgreSQL (planificado, no construido)

Hay un plan para añadir **PostgreSQL como copia derivada y de solo lectura** del Índice de Cumplimiento, sin tocar Mongo ni ningún endpoint
existente: [09 · PostgreSQL, fase 1](09-postgresql-fase-1.md). Mientras no se ejecute, el sistema no usa SQL.
