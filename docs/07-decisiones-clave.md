# 07 · Decisiones clave

Las decisiones de diseño que explican por qué el backend es como es. Son las esenciales de un registro más largo que se
retiró de `docs/`; el texto completo de cada ADR sigue en el historial:

```bash
git show pre-limpieza-docs:docs/design-decisions.md
```

Cada fila tiene su número de ADR para buscarla ahí. Las decisiones tomadas después de la limpieza se añaden al final (§3).

## 1. Decisiones que se conservan

| ADR | Decisión | Por qué |
|---|---|---|
| 001 | **Arquitectura Limpia** con puertos y adaptadores | Dominio testeable sin framework; SOLID demostrable. El MVC anterior mezclaba lógica en los controladores |
| 002 | La regla de capas se verifica con **ArchUnit** en la build | Que `domain/` no importe Spring ni Mongo no depende de la disciplina de nadie: si se rompe, la build falla |
| 003 | **MongoDB** para datos y **Redis** para estado efímero | Cortes = documentos variables + consultas geoespaciales; Redis para rate limiting, ventana de consenso y pub/sub |
| 004 | Consumir la **API REST de Acuacar**, no scrapear HTML | Fuente oficial estable y sin conflicto con `robots.txt` |
| 005 | Respetar `robots.txt` aunque sea evadible; sin disfrazar el `User-Agent` | Coherencia ética del proyecto (ver `06-etica-de-datos.md`) |
| 007 | Reportes ciudadanos **sin registro**, con rate limiting y consenso | El usuario es un vecino sin agua en el celular: cualquier fricción lo pierde |
| 014 | Un barrio sin dato verificado se publica **sin estado**, no como «con servicio» | Un falso «con servicio» es peor que un «sin datos» |
| 018 | Rate limiting HTTP **por IP**, configurable por ruta | Freno barato; el bloqueo por cuenta cubre el ataque repartido |
| 022 | El Índice de Cumplimiento agrega **sumando duraciones**, no promediando porcentajes | El promedio de porcentajes sobrepesa los cortes cortos |
| 025 | **Sin inteligencia artificial** en el proyecto | Cumplir plazos; la extracción es determinista (expresiones regulares) |
| 028 | La ingesta automática **propone**; publicar es decisión del veedor (con excepción de los boletines de Acuacar, ADR-034) | Una plataforma que desmiente rumores no puede publicar sin revisión |
| 039 | Panel con **cuentas individuales**, rol y permisos ajustables por persona (reemplaza a ADR-016, credencial compartida) | Cada acción queda atribuida a una persona |
| 057 / 080 | El proyecto **corre en local**, sin hosting, dominio ni CDN | Es un proyecto académico |
| 086 | Arranque **sin `.env`**: el backend genera su secreto de sesión y la clave del primer ADMIN | Que cualquiera pueda levantarlo sin configurar nada |

## 2. ADR-015 — Consultas de solo lectura: del controlador al puerto de salida

**Estado: aceptada, y confirmada en la revisión de arquitectura del 2026-09-29.**

Para una consulta **sin regla de negocio** (listar sectores, leer la bitácora, mostrar la cola de moderación), el controlador
depende de un puerto de salida (`domain/port/out`) directamente, sin un caso de uso intermedio que solo delegue.
Los controladores afectados son `SectorController`, `CorteController` (lectura), `BitacoraController`, `IngestaRevisionController`,
`ModeracionReporteController` e `IngestaFallidosController`.

- **Gana:** no se inventan clases vacías en `application/`; las dependencias siguen apuntando hacia adentro y ArchUnit sigue en verde.
- **Pierde:** es una excepción a «controlador ↔ caso de uso», y las excepciones se erosionan si nadie las vigila.
- **Límite explícito:** en cuanto una consulta necesite una regla (filtrar por frescura, combinar sectores con cortes, calcular un
  agregado), deja de ser del controlador y pasa a ser un caso de uso. Un `if` de negocio en un controlador de lectura viola la decisión.
- **Se revierte** creando el caso de uso y apuntando el controlador a él; adaptador, DTO y contrato no cambian.

Deuda conocida relacionada (no bloquea): parte del parseo de filtros vive en los controladores (`BitacoraController`, `AdminUsuariosController`)
y algunos DTO se arman con métodos estáticos `de(...)` en vez de MapStruct. Es candidato a limpieza posterior, sin cambio de comportamiento.

## 3. Decisiones tomadas en la limpieza del 2026-09-29

| Decisión | Motivo |
|---|---|
| La documentación anterior se retiró de `docs/`; queda en el historial bajo la etiqueta `pre-limpieza-docs` | Partir de documentación nueva, corta y validada contra el sistema real |
| `docs/api/` y `docs/diseno/` se conservan | Son la referencia del frontend (Yordy) |
| `DESIGN.md` se conserva | `frontend/src/estilos/tokens.test.ts` lo lee para validar los colores: borrarlo rompe el CI del frontend |
| `docker compose up` **no siembra**; el sembrador está bajo el perfil `siembra` | Que levantar el sistema a mano parta de una base vacía y sembrar sea un paso explícito |
| Rutas **denegadas por defecto** en `SecurityConfig` | Un endpoint nuevo no queda público por descuido |
| Bloqueo de login por **cuenta y dirección**, con tope global por cuenta | Conocer un correo ya no basta para bloquear a su titular |
| La clave de las cuentas de demostración se genera en cada siembra | Una clave fija en un repo público era una puerta abierta |
| Guardar un corte, su bitácora y el estado de sus sectores es **una sola transacción** | Un fallo a medias dejaba el corte registrado con los sectores sin mover |
| El secreto TOTP sigue en claro en Mongo (riesgo aceptado) | Cifrarlo exige una clave persistente que rompe el arranque sin configuración; Mongo solo escucha en `127.0.0.1` |
| El historial de git no se reescribe | Obligaría a Yordy a volver a clonar; las claves expuestas se tratan como comprometidas y no se reutilizan |
