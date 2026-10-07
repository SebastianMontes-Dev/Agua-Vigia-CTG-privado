# R9 · Sistema y cierre

**Objetivo:** mover lo último a `sistema/`, borrar la estructura vieja, dejar ArchUnit final, podar la suite de tests,
quitar dependencias que ya no se usan y actualizar la documentación y las herramientas del agente.

Riesgo: medio. Esfuerzo: 2 sesiones.

## 1. Mover `sistema/`

```
sistema/
├── simulacion/
│   ├── SimController · GuardiaDeSimulacion        (mismas rutas /api/sim/**, misma clave X-Sim-Key, 503 sin SIMULACION_CLAVE)
│   ├── SimulacionService      ControlarReloj + InyectarBoletin + Reiniciar + IniciarSesionDeAdmin
│   ├── RelojSimulado          implementa compartido/reloj/Reloj (+ RelojControlablePort absorbido)
│   ├── BoletinSimulado · EstadoDelReloj · DisparadorDeIngesta · SimulacionConfig
│   └── SimDtos                BoletinSimuladoRespuesta, EstadoDelRelojRespuesta, SolicitudBoletinSimulado, SolicitudCambioDeReloj
├── MetricasController · SistemaController · IotController · Open311Controller (R6)
├── SistemaService             ConsultarMetricas + ConsultarModo + RegistrarLecturaDePresion
├── MetricasEnMemoria          (+ MetricasDelSistemaPort absorbido; LongAdder igual)
├── MetricasDelSistema · ModoDelSistema
├── MantenimientoConfig · MantenimientoProperties
└── SistemaDtos                IotCoordenada, IotPresionRequest, MetricasDelSistemaRespuesta, ModoDelSistemaRespuesta
```

Las tareas de fotos (`LimpiezaFotosHuerfanasJob`, `PurgaEvidenciaAntiguaJob`) ya se movieron a `reportes/fotos/` en R4.

## 2. Borrar lo viejo

- Borrar `domain/`, `application/`, `api/` e `infrastructure/`. Tienen que estar vacíos o contener solo `package-info`; si queda algo, se mueve antes.
- Borrar `CasosDeUsoConfig` y `TransaccionPort`.
- Comprobar que en `src/main` no queda ninguna interfaz con una sola implementación, salvo la convención 4 del README:

  ```bash
  grep -rl "^public interface" backend/src/main/java
  ```
- **`pom.xml`:**
  - quitar `mapstruct` y `mapstruct-processor`
  - quitar `lombok` si ya no se usa (`grep -r lombok backend/src/main`)
  - **conservar** `springdoc`, `resilience4j`, `jjwt`, `actuator`, `validation`, `mail` y `security`

## 3. ArchUnit final

En `ReglaDeOroArchitectureTest`:
- Se borran las reglas viejas y la constante `PAQUETES_NUEVOS`.
- Se quita `allowEmptyShould` de las reglas nuevas.
- Quedan exactamente las de [invariantes §5](invariantes.md#5-reglas-de-archunit-que-se-conservan-con-otra-forma):
  - reglas puras
  - controladores sin almacenes
  - `compartido` independiente
  - solo `bitacora` crea eventos
  - único escritor del estado de un barrio
  - `@PreAuthorize` en panel y vecino
  - controladores sin `@EventListener`/`@Scheduled`

## 4. Podar la suite de tests

La meta es menos código de test **sin perder casos de negocio**.

**Se borran:**
- tests de clases que ya no existen y que no probaban comportamiento, sino cableado: `*ConfigTest` que solo comprueban que un bean existe, `CableadoDelRelojTest`, `MetricasDelSistemaPortNingunaTest`
- `*Test` de puertos y adaptadores que quedaron duplicados con el test del almacén

**Se unen:** los tests de un mismo almacén o servicio que quedaron repartidos (`*AdapterTest` + `*AdapterCacheTest` + `*AdapterTransaccionTest` → `XAlmacenTest`).

**No se borra nunca:**
- un test de regla de negocio, de controlador o de integración
- un test de seguridad
- un test de operación atómica

Cada test borrado va en el mensaje del commit con su motivo.

## 5. Documentación y herramientas

- **`CLAUDE.md`:** se reescribe la sección de arquitectura con la estructura por funcionalidad, las convenciones del README y la regla de oro nueva. Se quita la sección de transición y la excepción de la rama.
- **`docs/02-arquitectura.md` (nuevo)** es la guía de la sustentación:
  - la estructura
  - el molde de una funcionalidad
  - el mapa de quién usa a quién
  - por qué hay un ciclo deliberado
  - las reglas puras y las reglas ArchUnit
  - qué se ganó (cifras de la tabla de Avance)

  Se enlaza desde `docs/README.md` (hoy figura «02 · Arquitectura — Pendiente»).
- **`docs/07-decisiones-clave.md`:** ADR-098 pasa a «aceptada y ejecutada», y las filas 001/002 apuntan a ella.
- **`.claude/skills/verificar-arquitectura`** y **`.claude/agents/revisor-dominio.md`**: se reescriben con las reglas nuevas, porque hoy buscan imports en `domain/`.
- **`docs/api/`** no se toca: el contrato no cambió. Si por error cambió, R9 no se cierra hasta revertirlo.
- **Esta carpeta `docs/reduccion/`** queda como historia, y el README se marca como «terminado».

## Terminado cuando

- La puerta está en verde, con ArchUnit final, sin reglas de transición.
- `scripts/reduccion/medir.sh` → fila final en Avance.
- `git diff pre-reduccion -- backend/openapi.yaml` está vacío. Si hubo que regenerarlo, la diferencia es solo de orden o redacción.
- La rama `refactor/reduccion-backend` se fusiona a `main` por última vez y se borra (en local y en remoto). Se crea la etiqueta `reduccion-terminada`.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R9-sistema-y-cierre.md.
Ejecuta R9 en refactor/reduccion-backend (antes: git merge main): mueve sistema/, borra la estructura vieja,
CasosDeUsoConfig y TransaccionPort, deja ArchUnit final, quita MapStruct (y Lombok si no se usa), poda tests según §4
anotando cada borrado en el commit, y actualiza CLAUDE.md, docs/02-arquitectura.md, docs/07, la skill verificar-arquitectura
y el agente revisor-dominio. Corre la puerta completa y muéstrame la salida y la tabla de Avance.
No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
