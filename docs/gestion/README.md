# Gestión del proyecto

> Cómo se organiza el trabajo, qué se registra y dónde. Si buscas **qué** construimos, ve a
> `docs/brief.md`; aquí está **cómo se lleva la cuenta**.

---

## Qué hay en esta carpeta

| Archivo | Para qué | Cuándo se toca |
|---|---|---|
| [`protocolo-de-contexto.md`](protocolo-de-contexto.md) | Reglas de ahorro de tokens y dónde vive cada dato | **Léelo antes de tu primera sesión** |
| [`bitacora-sesiones.md`](bitacora-sesiones.md) | Qué hizo cada sesión de trabajo con IA | Al cerrar cada sesión |
| [`registro-de-bugs.md`](registro-de-bugs.md) | Defectos, causa raíz y corrección | Al encontrar un bug |
| [`registro-de-implementaciones.md`](registro-de-implementaciones.md) | Qué requisito pasó a funcionando | Al fusionar un PR a `main` |
| [`recomendaciones-ia.md`](recomendaciones-ia.md) | Observaciones de mejora que la IA detecta al trabajar | Cuando la IA nota algo |
| [`plantilla-sprint.md`](plantilla-sprint.md) | Objetivo, compromisos, review y retrospectiva | Al abrir y cerrar cada sprint |
| `sprint-N.md` | Un archivo por sprint, desde la plantilla | Durante el sprint |
| `historico/` | Registros rotados de sprints cerrados | Al cerrar el sprint |

---

## Las reglas de registro — no negociables

Son parte de la definición de terminado del proyecto, no una formalidad.

**1. Toda implementación se registra.**
Al fusionar un PR a `main`, una fila en `registro-de-implementaciones.md` con su `RF`, su PR y su
prueba. Un requisito sin fila no cuenta como implementado, aunque el código exista. **Aplica a todo
PR fusionado**, no solo al que implementa un requisito: el andamiaje, la infraestructura y los
cambios de proceso llevan `RF = —` y su `Tipo`, y no suman a la cobertura (`ADR-009`).

**2. Todo bug se registra al encontrarlo**, aunque se arregle en el acto.
Con severidad, causa raíz y la prueba que impide que vuelva. Un bug arreglado sin prueba es un bug que
regresa en el siguiente sprint.

**3. Toda sesión de trabajo con IA se cierra con su entrada en la bitácora.**
Tres líneas: qué se logró y cuál es el siguiente paso. Es lo que permite retomar mañana sin
reconstruir la conversación.

**4. Quien avanza, actualiza el registro que le toca — yo o la IA, sin excepción.**
Terminar una funcionalidad o arreglar un bug **no está completo hasta que su registro lo dice**. No
es burocracia: la Sala de control se genera de estos archivos y de nada más.

Las tres primeras tienen skill propia: `registrar-implementacion`, `registrar-bug`, `cerrar-sesion`.

---

## La Sala de control — se genera sola, si actualizas tu registro

`dist-dashboard/index.html` (ignorado por git), generado por `scripts/generar-dashboard.mjs`. **Ya no se publica**: se regenera a
mano y se abre en local.

```bash
node scripts/generar-dashboard.mjs
```

**Nadie edita ese HTML. No se puede "arreglar el tablero" a mano.** El generador lee PRs e issues
reales con `gh` y parsea los archivos de esta carpeta; si un dato no está escrito donde toca, para el
tablero no existe. Lo que muestra cada sección y de dónde sale:

| En la Sala de control se ve | Sale de |
|---|---|
| Qué falta para cerrar el sprint | `sprint-N.md` §2 — `✅` hecho · `🟡` parcial · sin marca, pendiente. Sirve al **principio del Entregable** (`✅ Entregado — …`, como se viene marcando) o en una columna `Estado` al final de la fila |
| Objetivo y criterio de cierre del sprint | `sprint-N.md` §1 y el paréntesis de `**Cerrado:** —` en la cabecera |
| Bugs, con severidad | `registro-de-bugs.md` — tabla de estado |
| Cobertura por módulo | `registro-de-implementaciones.md` — tabla **Estado de cobertura** |
| Decisiones pendientes | `docs/design-decisions.md` — campo **Estado** de cada ADR |
| Recomendaciones de la IA | `recomendaciones-ia.md` |

**Si cambias el formato de una de esas tablas, se rompe el extractor.** Antes de fusionar un cambio
de formato, corre el comando de arriba y revisa que los conteos que imprime al final sigan siendo los
correctos: avisa cuando una sección queda vacía.

---

## Los siete sprints

**7 sprints. Sprint 0 de preparación + 6 de construcción.** No tienen duración fija.

**Un sprint se marca como completado cuando su entregable se demuestra funcionando, no cuando se
acaba la semana.** La columna "Entregable que lo cierra" es la definición, no una aspiración: mientras
eso no se pueda mostrar corriendo, el sprint sigue abierto por rápido que se haya ido; y cuando se
puede mostrar, el sprint cierra aunque hayan pasado tres días.

| Sprint | Foco | Entregable que lo cierra |
|---|---|---|
| **0** | Documentación, infraestructura, contratos | Repositorio operativo, `docker compose up` funcionando |
| **1** | Mapa base y dominio core | Mapa mostrando sectores reales de Cartagena |
| **2** | Reporte ciudadano y consenso | Un vecino reporta en 2 toques y el consenso cambia el estado |
| **3** | Administración y alertas | El veedor registra un corte y el suscriptor recibe el correo |
| **4** | Ingesta y Cumplimiento ⭐ | Un boletín real de Acuacar entra solo y se calcula su índice |
| **5** | Calidad, accesibilidad y PWA | Cobertura ≥ 70%, auditoría WCAG AA, E2E en verde (la parte de interfaz —WCAG, PWA, E2E— espera al frontend nuevo, `ADR-048`) |
| **6** | Entrega final | Demo corriendo en local en los PC del equipo (`ADR-057`), dataset histórico cargado |

---

## Ritmo de cada sprint

| Momento | Qué se deja escrito |
|---|---|
| **Al abrir** | Objetivo del sprint y compromisos en `sprint-N.md` |
| **Al cerrar** (cuando el entregable se demuestra corriendo) | Qué se demostró funcionando, en `sprint-N.md`, y la rotación de registros (`protocolo-de-contexto.md` §5) |
| **Retrospectiva** | Máximo 3 acciones concretas con fecha |

**La retrospectiva no produce buenos deseos.** "Ser más ordenado" no es una acción. "Regenerar el
`openapi.yaml` cada vez que cambie un controlador" sí lo es.

---

## Definición de terminado

Aplica a cualquier entregable.

- [ ] El código pasa la build completa: compila, tests, ArchUnit, linter
- [ ] Tiene pruebas que cubren el flujo principal y al menos un caso de borde
- [ ] Si es un cambio no trivial, entró por Pull Request enlazando su `RF`
- [ ] Fila agregada en `registro-de-implementaciones.md`
- [ ] Si tocó una decisión de diseño → ADR en `docs/design-decisions.md`
- [ ] Si reveló un hallazgo que costó descubrir → línea en `MEMORY.md`
- [ ] La sesión de trabajo quedó cerrada en `bitacora-sesiones.md`
