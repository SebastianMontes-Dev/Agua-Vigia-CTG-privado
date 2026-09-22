# Plan de acción — validación y preparación del backend

**Objetivo:** dejar el backend verificable y reproducible en local para integrar el futuro frontend.
**Base de planificación:** `main` en `6500e25`. Este plan ordena trabajo; el estado de cada tarea se lleva en GitHub Issues/Projects, no aquí.

## Punto de partida

| Evidencia comprobada sobre `6500e25` | Consecuencia para el plan |
|---|---|
| `backend/pom.xml` usa Spring Boot 3.5.x y Testcontainers 1.21.3 (`ADR-059`, `ADR-060`). | Las actualizaciones de dependencias se revisan contra esos ADR; no se presupone una migración mayor. |
| JaCoCo aplica el 85 % a `com.aguavigia.ctg.domain.*` y `com.aguavigia.ctg.application.*`. | Hay que comprobar y corregir si los paquetes raíz quedan fuera de la regla. |
| `ContratoOpenApiTest` compara conjuntos de rutas del contrato vivo y el versionado; además prueba algunos casos de seguridad concretos. | La sincronía completa de métodos, parámetros, cuerpos, esquemas, respuestas y seguridad no está cubierta. |
| La etiqueta `pre-retiro-frontend` no apareció ni localmente ni en `origin`; varios documentos afirman que existe. | Resolver la referencia histórica en la fase 0, tras verificar el commit exacto al que debe apuntar. |
| El arreglo del escaneo de secretos ya entró a `main` en `6500e25`. | No se incluye como pendiente. |

El diagnóstico previo sobre `608f4b9` informó 693 pruebas locales sin Docker, 825 en CI con una omitida y un sistema completo aún sin arrancar localmente. Son **cifras históricas, no la línea base de esta ejecución**. Las carreras, los cambios parciales de estado y la atribución de auditoría descritos allí se tratarán como hipótesis hasta reproducirlos con pruebas. La fuente operativa del estado general sigue siendo [estado-del-backend.md](estado-del-backend.md).

## Fases y puertas de salida

| Fase | Trabajo y entregable revisable | Puerta de salida |
|---|---|---|
| **0. Fijar la base** | Ejecutar build y suite completa con Docker, registrar versiones y resultados; revisar los PR de dependencias abiertos; verificar la ascendencia del commit de retirada antes de crear la etiqueta histórica; corregir referencias y datos obsoletos de arranque, pruebas y migración. | Commit base identificado; suite y límites del entorno anotados con evidencia; referencia histórica comprobable; documentación coherente. |
| **1. Reparar controles de calidad** | Incluir paquetes raíz y subpaquetes en JaCoCo; ampliar ArchUnit para que dominio dependa solo de Java y aplicación solo de dominio y puertos; comparar semánticamente OpenAPI vivo y versionado; incluir cambios de workflows en sus disparadores de CI. | `verify` pasa con Mongo y Redis; una reducción deliberada de cobertura bajo 85 % falla; cambios incompatibles de método, campo o respuesta fallan; reglas arquitectónicas fallan ante dependencias prohibidas. |
| **2. Estabilizar el estado y la auditoría** | Calcular una sola transición por sector para avisos vigentes y vencidos; hacer que un corte aprobado vigente prevalezca; corregir el autor registrado al reactivar una cuenta (`BUG-095`). | Casos con avisos solapados dan el mismo resultado en cualquier orden; una segunda ejecución no crea eventos ni notificaciones; autor y sujeto afectado quedan distintos y correctos. |
| **3. Asegurar persistencia y concurrencia** | Respaldar y restaurar Mongo en un ensayo verificable; configurar el Mongo local como replica set de un nodo; agrupar estado y bitácora en transacciones; emitir notificaciones e invalidaciones tras confirmar; serializar y revalidar cambios del último administrador mediante un documento de control compartido. Los mecanismos tecnológicos viven en adaptadores detrás de puertos. | Una falla intermedia revierte estado y evento; los reintentos no duplican eventos; operaciones concurrentes no dejan cero administradores activos; respaldo y restauración se comprueban antes de migrar datos existentes. |
| **4. Completar la separación de capas** | Mover configuración, eventos y ejecución asíncrona a infraestructura; dividir servicios con varias acciones de negocio; sacar reglas de negocio de controladores y completar DTOs/MapStruct. Conservar la excepción de consultas simples de `ADR-015`. | ArchUnit y suite completa pasan; rutas, DTOs públicos y comportamiento HTTP mantienen compatibilidad; los casos de uso no dependen de Spring ni Mongo. |
| **5. Entregar el entorno al frontend** | Probar arranque limpio de Mongo, Redis, correo de pruebas y API; sembrar sectores y administrador; alinear CORS del perfil Docker con el origen local elegido; ejecutar flujos HTTP reales; actualizar guía de consumo, contrato y matriz con resultados. | Otra instalación puede reproducir el arranque sin archivos personales ni datos previos, y ejecutar los flujos de la lista de aceptación. |

Las fases se realizan en este orden; cada una se integra como cambio revisable con sus pruebas y registros. Si la fase 0 descubre una falla que impide correr la suite, se corrige como parte de la base antes de usar los resultados como referencia. Las fases 2 y 3 pueden requerir cambios en los mismos servicios: primero se fija el resultado de negocio y luego su atomicidad.

## Pruebas de aceptación final

| Área | Evidencia exigida |
|---|---|
| Calidad | Suite local y CI completas, integración con Mongo y Redis, cobertura real ≥ 85 % en dominio y aplicación, ArchUnit y comparación semántica OpenAPI. |
| Estado y consistencia | Solapamientos en distinto orden, repetición sin efectos nuevos, reversión ante error y concurrencia sobre el último administrador. |
| Auditoría | Evento y estado coherentes; autor y usuario afectado identificados correctamente. |
| API | Demostración HTTP de sectores, reportes, consenso, cortes, suscripciones, cuentas, TOTP, permisos, fotos, estadísticas y SSE. |
| Operación local | Instalación desde cero, CORS comprobado desde el origen del frontend, respaldo y restauración de Mongo demostrados. |

**Límites:** se conservan Swagger, correos HTML, páginas auxiliares y herramientas internas. No se prevén cambios incompatibles en rutas o DTOs públicos. El webhook real de WhatsApp/Telegram (`RF041`), el despliegue público y la demostración de 50 000 usuarios simultáneos quedan fuera de esta preparación (`ADR-057`).

**Registro durante la ejecución:** cada defecto confirmado va a `docs/gestion/registro-de-bugs.md`; cada elección técnica nueva, a `docs/design-decisions.md`; cada cambio de comportamiento, a `docs/ingenieria/comportamiento-del-sistema.md`; y el avance de un compromiso de sprint, a su `docs/gestion/sprint-N.md`. El detalle de requisitos y pruebas vive en [matriz-trazabilidad.md](matriz-trazabilidad.md) y [plan-de-pruebas.md](plan-de-pruebas.md).
