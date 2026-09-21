## Qué hace este PR

<!-- Una o dos frases, en pasado y con el resultado. No la intención. -->

**Requisito:** RF0NN / RNF0NN
**Módulo:** M<N>
**Cierra:** #<issue>

---

## Cómo se verifica

<!-- Nombre de la prueba automatizada. "Probado manualmente" no es una prueba. -->

- Prueba:
- Caso de borde cubierto:

---

## Antes de fusionar

- [ ] La build pasa completa: compila, tests, **ArchUnit**, linter
- [ ] Hay prueba del flujo principal **y** de al menos un caso de borde
- [ ] `domain/` no importa `org.springframework` ni `com.mongodb`
- [ ] No expongo entidades de dominio en la API (van DTOs)
- [ ] Sin credenciales, tokens ni URLs con secretos en el diff
- [ ] Commits en Conventional Commits, en español e imperativo

## Si aplica

- [ ] Si tomé una decisión de diseño → **ADR** en `docs/design-decisions.md`
- [ ] Si descubrí algo que costó descubrir → línea en `MEMORY.md`
- [ ] Si arreglé un bug → registrado en `docs/gestion/registro-de-bugs.md` con causa raíz

## Al fusionar (no antes)

- [ ] Fila agregada en `docs/gestion/registro-de-implementaciones.md` (skill `registrar-implementacion`)

### Lo que este PR le cambia a la Sala de control — obligatorio

La Sala de control (`dist-dashboard/index.html`) se regenera con `node scripts/generar-dashboard.mjs`: **nadie
edita el HTML**. Pero solo sabe lo que estos archivos digan, así que un PR que avanza el trabajo sin
actualizarlos deja el tablero mintiendo. Marca lo que corresponda — si no aplica ninguno, di por qué
en el cuerpo del PR.

- [ ] **Compromiso del sprint** entregado o a medias → `docs/gestion/sprint-N.md` §2, marcando `✅ Entregado — …` o `🟡 …` al principio de su Entregable
- [ ] **Bug** encontrado o cerrado → `docs/gestion/registro-de-bugs.md` (skill `registrar-bug`)
- [ ] **Requisito demostrado en review** → tabla de cobertura por módulo en `registro-de-implementaciones.md`

---

## Notas

<!-- ¿Qué merece atención al releer el diff? Sé específico. -->
