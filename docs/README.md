# Documentación de AguaVigía CTG — por dónde empezar

> Una pantalla para no perderse. Cada dato vive en un solo archivo (`gestion/protocolo-de-contexto.md` §2); aquí solo hay
> punteros. **Vigente** = describe el sistema de hoy. **Histórico** = registro fechado que no se reescribe.

## Para la sustentación

| Necesito… | Leo |
|---|---|
| Levantar todo con un comando y entrar al panel | [`../README.md`](../README.md) §Cómo levantar · [`ingenieria/entorno-local.md`](ingenieria/entorno-local.md) |
| El orden de la demo, con los comandos | [`ingenieria/guion-de-demo.md`](ingenieria/guion-de-demo.md) |
| Qué cuentas existen y con qué clave | [`ingenieria/credenciales-y-accesos.md`](ingenieria/credenciales-y-accesos.md) |
| Cuánta carga aguanta y cómo demostrarlo | [`ingenieria/escalabilidad.md`](ingenieria/escalabilidad.md) · [`../scripts/carga/README.md`](../scripts/carga/README.md) |
| Qué está hecho y qué falta, con cifras | [`ingenieria/estado-del-backend.md`](ingenieria/estado-del-backend.md) |
| Requisito por requisito, con la prueba que lo sostiene | [`ingenieria/matriz-trazabilidad.md`](ingenieria/matriz-trazabilidad.md) |

## Vigente

| Tema | Archivo |
|---|---|
| Qué construimos y para quién | [`brief.md`](brief.md) |
| Requisitos (RF / RNF) | [`product-requirements.md`](product-requirements.md) |
| Qué hace el sistema, en escenarios | [`ingenieria/comportamiento-del-sistema.md`](ingenieria/comportamiento-del-sistema.md) |
| Por qué se decidió cada cosa (ADR) | [`design-decisions.md`](design-decisions.md) — append-only: los reemplazados lo dicen en su «Estado» |
| Contrato de la API y guía para el frontend | [`../backend/openapi.yaml`](../backend/openapi.yaml) · [`api/`](api/README.md) |
| Arquitectura y dominio | [`ingenieria/diagrama-de-componentes.md`](ingenieria/diagrama-de-componentes.md) · [`ingenieria/diagrama-de-clases.md`](ingenieria/diagrama-de-clases.md) · [`ingenieria/modelo-de-dominio.md`](ingenieria/modelo-de-dominio.md) |
| Fuentes de datos y su veredicto | [`ingenieria/auditoria-fuentes-de-datos.md`](ingenieria/auditoria-fuentes-de-datos.md) |
| Plan de pruebas | [`ingenieria/plan-de-pruebas.md`](ingenieria/plan-de-pruebas.md) |
| Respaldo y restauración de Mongo | [`ingenieria/respaldo-y-restauracion.md`](ingenieria/respaldo-y-restauracion.md) |
| Anexos académicos (historias de usuario, manual técnico) | [`anexos/`](anexos/) |
| Modelo NoSQL (BD2) | [`ingenieria/transformacion-er-a-nosql-bd2.pdf`](ingenieria/transformacion-er-a-nosql-bd2.pdf) |
| Cómo habla el frontend con el backend | [`ingenieria/integracion-frontend-backend.md`](ingenieria/integracion-frontend-backend.md) |
| Frontend (de Yordy) | [`ingenieria/plan-frontend.md`](ingenieria/plan-frontend.md) · [`diseno/`](diseno/) · [`../DESIGN.md`](../DESIGN.md) |

## Histórico (registros fechados)

| Registro | Archivo |
|---|---|
| Sprints 0–7 y los 8 entregables | [`gestion/README.md`](gestion/README.md) · `gestion/sprint-N.md` |
| Implementaciones, bugs, sesiones con IA, recomendaciones | [`gestion/registro-de-implementaciones.md`](gestion/registro-de-implementaciones.md) · [`gestion/registro-de-bugs.md`](gestion/registro-de-bugs.md) · [`gestion/bitacora-sesiones.md`](gestion/bitacora-sesiones.md) · [`gestion/recomendaciones-ia.md`](gestion/recomendaciones-ia.md) |
| Lo ya rotado de esos registros | [`gestion/historico/`](gestion/historico/README.md) |
| Diseño original de la ingesta (Sprint 0) | [`ingenieria/pipeline-ingesta-datos.md`](ingenieria/pipeline-ingesta-datos.md) — su cabecera dice en qué difiere lo construido |
| Planes ya ejecutados | [`ingenieria/plan-validacion-backend.md`](ingenieria/plan-validacion-backend.md) |
