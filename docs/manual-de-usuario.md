# Manual de usuario — AguaVigía CTG

Este manual describía la aplicación web anterior (el mapa, el formulario de reporte, el panel del veedor), que se retiró
(`ADR-048`; sigue en el historial de git y en la etiqueta `pre-retiro-frontend`). **El frontend nuevo está en `frontend/`,
en `main`, a medio construir**: el mapa, la historia pública y los avisos (F2–F4) ya existen; el panel del veedor (F5) tiene
avance en una rama sin fusionar y la integración (F6) falta (`docs/gestion/sprint-7.md`). Por eso este manual todavía no se
reescribe: se hará contra la interfaz terminada.

Lo que hoy sirve para entender qué puede hacer cada persona con el sistema:

| Si quieres… | Lee |
|---|---|
| Ver qué hace el sistema para cada tipo de persona, en orden de llamadas | [`docs/api/flujos-de-usuario.md`](api/flujos-de-usuario.md) |
| Construir el frontend nuevo (rutas, errores, límites, correos) | [`docs/api/README.md`](api/README.md) |
| Conocer las reglas de negocio con sus números | [`docs/api/datos-de-referencia.md`](api/datos-de-referencia.md) |
| Saber cómo se comporta el sistema (escenarios verificables) | [`docs/ingenieria/comportamiento-del-sistema.md`](ingenieria/comportamiento-del-sistema.md) |

Cuando el frontend nuevo cierre F6, su manual de usuario debe escribirse contra esa interfaz, no contra la retirada.
