# Guía de contribución

Convenciones para commits y para subir el trabajo en este repositorio. El objetivo es que el
historial de `main` sea legible y predecible, sin importar quién (o qué herramienta) escriba el código.

## Commits

Formato: [Conventional Commits](https://www.conventionalcommits.org/) con descripción en español.

```
tipo(scope): descripción en español, imperativo, sin punto final

Cuerpo opcional: explica el POR QUÉ del cambio, no el qué — el diff ya
dice qué cambió. Usalo cuando el motivo no sea obvio (un bug no evidente,
una restricción externa, una decisión de diseño no trivial).
```

**Tipos** (siempre en inglés, son parte del estándar):

| Tipo | Uso |
|---|---|
| `feat` | Funcionalidad nueva |
| `fix` | Corrección de un bug |
| `docs` | Solo documentación (README, docs/, comentarios) |
| `refactor` | Cambio de estructura interna sin alterar comportamiento |
| `test` | Agregar o corregir tests, sin tocar código de producción |
| `chore` | Mantenimiento (dependencias, configuración, tooling) |
| `perf` | Mejora de rendimiento |
| `style` | Formato/estilo sin efecto en lógica |
| `build` | Cambios en el sistema de build (Maven, Docker) |
| `ci` | Cambios en workflows de GitHub Actions |
| `revert` | Revertir un commit anterior |

**Scope** (opcional, entre paréntesis): el nombre del área o módulo real que tocaste, en español,
igual que aparece en el código — no hay una lista cerrada. Ejemplos ya usados en este proyecto:
`api`, `seguridad`, `ingesta`, `veedor`, `mapa`, `estadisticas`, `bitacora`, `gestion`,
`estilos`, `ci`.

Ejemplos:
```
feat(veedor): agregar verificación en dos pasos al login
fix(mapa): corregir el z-index del panel de detalle sobre Leaflet
docs(gestion): registrar ADR-045
chore: actualizar springdoc a 2.8.x
```

Nunca agregar `Co-Authored-By: Claude` (ni ninguna variante de atribución a la IA) — los commits
quedan únicamente bajo mi cuenta.

## Todo directo a `main`

**Todo se trabaja directo sobre `main`, sin ramas ni PR** (decisión del dueño, 2026-09-29): somos dos
personas en carpetas distintas (`backend/` y `frontend/`) y las ramas apiladas complicaron más de lo
que protegieron (con squash-merge, #108 y #110 se cerraron al borrarse sus bases). La red de seguridad
está antes de empujar:

- Antes de `git push`, la verificación local que corresponda: `./mvnw verify` si cambió `backend/`,
  `cd scripts && npm test` si cambiaron los scripts, `docker compose config --quiet` si cambió el compose.
- El CI corre igual en cada push a `main`. Si queda en rojo, se arregla en el siguiente commit, antes de
  seguir con otra cosa.
- Un commit por unidad de trabajo, con mensaje Conventional Commit (ver arriba): en `main` el historial es
  el de los commits, así que tienen que leerse solos.

## Idioma

Nombres de clases y commits en español (siguiendo la convención ya establecida del código:
`ServicioX`, `CasoUsoX`, `ControladorX`, `RepositorioX`). Los tipos de Conventional Commits
(`feat`, `fix`, etc.) se mantienen en inglés porque son parte del estándar y de la integración con
herramientas (changelogs automáticos, etc.).
