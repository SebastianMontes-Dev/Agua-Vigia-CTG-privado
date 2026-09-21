# Manual de usuario — AguaVigía CTG

Este manual describía la aplicación web (el mapa, el formulario de reporte, el panel del veedor). **El frontend
se retiró del repositorio** (`ADR-048`) y lo rehará otra persona, por lo que ese manual ya no corresponde a nada
que exista. La versión anterior sigue en el historial de git y en la etiqueta `pre-retiro-frontend`.

Lo que hoy sirve para entender qué puede hacer cada persona con el sistema:

| Si quieres… | Lee |
|---|---|
| Ver qué hace el sistema para cada tipo de persona, en orden de llamadas | [`docs/api/flujos-de-usuario.md`](api/flujos-de-usuario.md) |
| Construir el frontend nuevo (rutas, errores, límites, correos) | [`docs/api/README.md`](api/README.md) |
| Conocer las reglas de negocio con sus números | [`docs/api/datos-de-referencia.md`](api/datos-de-referencia.md) |
| Saber cómo se comporta el sistema (escenarios verificables) | [`docs/ingenieria/comportamiento-del-sistema.md`](ingenieria/comportamiento-del-sistema.md) |

Cuando exista el frontend nuevo, su manual de usuario debe escribirse contra esa interfaz, no contra esta.
