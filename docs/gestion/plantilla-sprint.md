# Plantilla de sprint

> Copia este archivo a `docs/gestion/sprint-N.md` al abrir cada sprint. Se llena en tres momentos:
> planning (§1–2), durante (§3) y cierre (§4–6).
>
> **Cabe en una página.** Un documento de sprint de cinco páginas no lo lee nadie.

---

# Sprint N — <foco en tres palabras>

**Abierto:** AAAA-MM-DD · **Cerrado:** — *(se llena el día que el entregable se demuestra funcionando)*

---

## 1. Objetivo del sprint

Una frase que describe qué podrá hacer un usuario al final que hoy no puede.

> Ejemplo: *Un vecino de Cartagena puede abrir el mapa y ver el estado real de su sector.*

**No sirve:** "avanzar en el backend", "terminar el sprint 2".

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de |
|---|---|---|
| | | |

La columna **Depende de** es la importante: es donde se ve qué tiene que existir antes. Se escribe
con el artefacto concreto que falta (una entidad de dominio, un endpoint, el contrato OpenAPI), no
con una intención.

---

## 3. Obstáculos del sprint — resumen

Lo que detuvo el avance y no dependía de escribir código: una credencial de un tercero que no llegó,
una fuente de datos caída, una decisión sin tomar. Un sprint con entregables retrasados y sin nada
anotado aquí significa que hubo obstáculos y no se registraron.

| Qué detuvo el avance | De qué dependía | Días | Cómo se resolvió |
|---|---|---|---|

---

## 4. Review — qué se demostró funcionando

Solo lo que se pudo **mostrar corriendo**. Código fusionado que no se puede demostrar no cuenta.

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|

**Comprometido:** N requisitos · **Entregado:** N · **Arrastrado al siguiente sprint:** N

---

## 5. Métricas del sprint

| Métrica | Valor |
|---|---|
| Requisitos entregados / comprometidos | |
| PRs fusionados | |
| Bugs abiertos / cerrados | |
| Cobertura `domain/` + `application/` | |
| Build en verde al cierre | Sí / No |

---

## 6. Retrospectiva

**Qué funcionó** — máximo 3, concretos.

**Qué no funcionó** — máximo 3. Sobre el proceso, no sobre uno mismo.

**Acciones para el próximo sprint** — máximo 3, cada una con fecha. Una acción sin fecha no se
ejecuta.

| Acción | Para cuándo |
|---|---|
