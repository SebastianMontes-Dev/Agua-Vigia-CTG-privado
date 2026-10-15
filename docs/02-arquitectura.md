# 02 · Arquitectura del backend

Estado a 2026-10-15 (hora de Cartagena), contrastado con el código de `main`. Las decisiones que explican cada pieza están en
[07 · Decisiones clave](07-decisiones-clave.md); aquí está el mapa.

## 1. Capas

Arquitectura Limpia (puertos y adaptadores, ADR-001). Las dependencias apuntan siempre hacia adentro:

```
api/  (REST, DTO, mappers)  ──┐
                              ├──►  application/  (casos de uso)  ──►  domain/  (Java puro: entidades, valores, reglas, puertos)
infrastructure/  (adaptadores) ┘                                          ▲
          └──────────── implementa los puertos de salida (domain/port/out) ┘
```

Paquete raíz: `backend/src/main/java/com/aguavigia/ctg/`.

| Capa | Qué contiene | Tamaño aprox. |
|---|---|---|
| `domain/` | Entidades (`CorteAgua`, `Sector`, `ReporteCiudadano`, `Usuario`…), objetos de valor como `record` que validan al construir, reglas de negocio (`ResolutorDeEstadoSector`, `ComposicionDelSustento`, `CompuertaDePublicacion`, `IndiceCumplimiento`…) y los **puertos**: `port/in` (casos de uso) y `port/out` (lo que el dominio necesita de fuera) | 115 clases + 57 puertos de entrada + 44 de salida |
| `application/` | Un caso de uso = una clase = una acción (`RegistrarReporteService`, `GestionarCorteOficialService`…). Orquesta dominio y puertos | 63 clases |
| `api/` | Controladores REST, DTO (`api/dto`) y mappers (`api/mapper`, MapStruct; algunos DTO usan métodos `de(...)`, ver ADR-015). Un único `@RestControllerAdvice` (`ManejadorGlobalDeErrores`) responde los errores en RFC 7807 | 36 + 49 DTO + 9 mappers |
| `infrastructure/` | Adaptadores: `persistence/` (Mongo, Redis y `sql/`, la copia analítica en PostgreSQL con Flyway), `ingest/` (colectores y pipeline), `security/` (JWT, TOTP, HMAC), `mail/`, `telegram/`, `sse/`, `cache/`, `ratelimit/`, `scheduling/`, `storage/` (fotos), `metricas/`, `sim/` (reloj de simulación), `config/` | ≈ 140 clases |

## 2. Reglas que la build hace cumplir

`backend/src/test/java/com/aguavigia/ctg/architecture/ReglaDeOroArchitectureTest.java` (ArchUnit, ADR-002). Si una se rompe, `./mvnw verify` falla:

- `domain/` no importa Spring ni MongoDB, y solo depende de Java y de sí mismo.
- `application/` no depende de `infrastructure/` ni de Spring Data/MongoDB: solo de dominio, Java y logging.
- `api/` no depende de `infrastructure/`, ni escucha eventos ni programa tareas.
- Un evento de bitácora solo se crea desde su fábrica o desde el adaptador Mongo.
- **Un solo escritor del estado de un barrio** (ADR-087): nada en producción cambia el estado saltándose el resolutor.
- Toda ruta del panel (`/api/veedor/**`) y del vecino (`/api/vecino/**`) exige un permiso.
- `domain/` y `application/` no importan JDBC, Flyway ni el driver de PostgreSQL (ADR-100); el paquete `persistence/sql` no escribe en Mongo por los repositorios.

Excepción documentada: los controladores de **lectura sin regla de negocio** dependen directamente de un puerto de salida, sin un caso de uso que
solo delegue (ADR-015).

## 3. Módulos funcionales

| Módulo | Piezas principales | Decisiones |
|---|---|---|
| Estado de un barrio | `ResolutorDeEstadoSector` decide con lo oficial, lo de la ingesta y lo de los vecinos; `PuestaAlDiaDeEstadosJob` lo mantiene al día | ADR-014, 087 |
| Reportes y consenso | `RegistrarReporteService`, cupo por dispositivo o cuenta, ventana de consenso en Redis, `ComposicionDelSustento` (un tercio verificado, redes mínimas), `EvaluacionPendienteJob` | ADR-007, 089, 090 |
| Cortes oficiales | `GestionarCorteOficialService`: registrar, avisar, cerrar barrio por barrio, anular; expiración de lo que nadie confirma | ADR-088 |
| Índice de Cumplimiento | `IndiceCumplimiento` y `CalcularCumplimientoService`: por par corte-barrio, sumando duraciones | ADR-022 |
| Ingesta | `PipelineOrquestador` → colectores (`AcuacarApiCollector`, `RssCollector`, `ColectorLocalDeBoletines`) → extracción determinista → `CompuertaDePublicacion` → propuesta o publicación | ADR-004, 025, 028, 092 |
| Restablecimiento | «¿Ya volvió el agua?» con un enlace de un toque al suscriptor | ADR-093 |
| Fotos de evidencia | Token de subida de un solo uso, moderación, una sola ruta de lectura | ADR-091 |
| Cuentas y panel | Registro, verificación, aprobación por un ADMIN, roles y permisos por persona, segundo factor TOTP, bloqueo, auditoría; cuentas de vecino | ADR-039, 089 |
| Avisos | Correo con doble confirmación (`MailNotificacionAdapter`), Telegram (`TelegramSondeoJob`) y tiempo real por SSE (`SseSectoresBroadcaster` + pub/sub de Redis) | — |
| Modo del sistema | Instancia real o de simulación (`RelojSimulado`, boletines simulados, métricas de calibración) | ADR-094 a 097 |

Los sensores IoT (`/api/iot/presion`) están construidos pero **inactivos**: no forman parte de la simulación ni de la demo.

## 4. Tareas programadas

| Tarea | Frecuencia | Cómo evita repetirse con varias instancias |
|---|---|---|
| `PipelineOrquestador` (ingesta) | 10 min | `EjecucionUnica` (candado en Redis) |
| `PlanificadorDeVentanas` (ventanas declaradas que empiezan o terminan) | 1 min | `EjecucionUnica` |
| `PuestaAlDiaDeEstadosJob` | periódica | `EjecucionUnica` |
| `CopiaAnaliticaJob` (copia de Mongo a PostgreSQL, ADR-100; solo con `aguavigia.sql.habilitado=true`) | 5 min, y una vez al arrancar tras 1 min | `EjecucionUnica` |
| `LimpiezaFotosHuerfanasJob`, `PurgaEvidenciaAntiguaJob` (esta, desactivada: ADR-097 §3) | periódica | `EjecucionUnica` |
| `EvaluacionPendienteJob` (consenso pendiente) | 1 s | Corre en todas las instancias; cada barrio pendiente lo reserva una sola en Redis |
| `TelegramSondeoJob` | 3 s | Solo existe si hay token de Telegram, y está pensado para una sola instancia |
| Latido SSE (`SseSectoresBroadcaster`) | periódica | Cada instancia atiende sus propias conexiones |

## 5. Perfiles de configuración

| Perfil | Uso |
|---|---|
| `docker` | El de `docker compose`: secretos generados al arrancar, Swagger abierto, CORS a los puertos locales del frontend |
| `prod` | Swagger cerrado, CORS explícito y `JWT_SECRET` obligatorio (`ValidacionDeSecretos`) |
| `carga` | Solo para las pruebas de carga: vacía el límite por IP y sube topes de conexiones |
| `dev` | Desarrollo fuera de Docker |

Seguridad de cada perfil: [05 · Seguridad y claves](05-seguridad-y-claves.md). Datos y almacenamiento: [03 · Base de datos](03-base-de-datos.md).
