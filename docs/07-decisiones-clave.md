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

## 4. Estado de un barrio, cierre de cortes e identidad (2026-10-01)

Decisiones de las fases F1 (ADR-087 y 088) y F2 (ADR-089 y 090) del plan de estados de barrio. Los números de ADR continúan los del registro histórico.

### ADR-087 — Un solo resolutor decide el estado de un barrio

**Estado: aceptada.**

El estado público de un barrio (`estadoActual` y sus marcas: origen, ventana prometida, por confirmar, disputa, respaldo) lo decide
**únicamente** `RecalcularSectorService`, que reúne lo que afirma cada fuente y se lo entrega a `ResolutorDeEstadoSector` (dominio puro,
función de las afirmaciones y de la hora). Las fuentes —boletín de Acuacar, nota de prensa aprobada, corte o cierre del veedor, quórum de
vecinos, sensores— solo aportan afirmaciones. Un reporte, un boletín aprobado, un corte, una moderación y el paso del tiempo son motivos para
volver a preguntar, no escritores.

- **Gana:** desaparece el «parpadeo» entre el consenso y el barrido por ventana, y los cortes de ingesta ya no dejan al barrio en «sin servicio»
  para siempre; el resolutor se prueba con una tabla de verdad, sin mocks.
- **Pierde:** `RecalcularSectorService` concentra mucho (afirmaciones, reapertura y cierre de cortes, escritura, bitácora); es candidato a
  dividirse más adelante.
- **Reglas que se fijaron:** las malas noticias viajan rápido y las buenas despacio (la promesa vencida no prueba que volvió el agua; confirmar un
  restablecimiento pide `max(2, ceil(umbral/2))` vecinos); un estado que solo sostienen los vecinos se recuerda y caduca a las 24 h sin reportes
  nuevos; un quórum contrario solo reabre el corte si **supera** al de restablecimiento; los sensores votan como los vecinos y se declaran
  `SENSOR` solo si el reporte entró por `/api/iot/presion`. Los sensores quedan construidos pero **inactivos** (el proyecto no usa sensores físicos y la ruta responde 503 sin `X-IoT-Key`).
- **Escritura:** compare-and-set del estado más una transacción para corte, estado y evento. Los cortes se vuelven a leer **dentro** de la
  transacción: si un veedor los cambió entre la lectura y la escritura, no se escribe nada. La disputa se anota una sola vez (`abrirDisputaSiEs`).
- **Se revierte** devolviendo la escritura a cada fuente; no se recomienda: reabre el problema que esta decisión cierra.

### ADR-088 — Un corte se cierra barrio por barrio, y lo que nadie confirma expira

**Estado: aceptada.**

Un corte agrupa varios barrios pero se restablece a horas distintas: cada barrio tiene su `CierreDeCorte` (hora, fuente, provisional) y el corte
pasa a `RESTABLECIDO` cuando todos están cerrados. `finReal` deja de existir como dato propio: es la hora del último cierre. Un cierre que solo
sostienen vecinos o sensores es **provisional** y lo confirma o corrige el veedor. Un corte que nadie cierra pasa a `EXPIRADO` a las 72 h del fin
prometido (configurable) y uno publicado por error se **anula** (`ANULADO`, con motivo y auditoría): ninguno entra al Índice de Cumplimiento.

- **Gana:** el Índice deja de tener sesgo a favor de Acuacar (los cortes más largos, que nadie cerraba, quedaban fuera); se puede representar un
  restablecimiento barrio por barrio y corregir un boletín mal leído sin falsear el histórico (la bitácora anexa la corrección, no se edita).
- **Pierde:** rompe el contrato (`finReal` → `cierres[]`); está documentado en `docs/api/cambios-para-frontend.md`.
- **Reapertura:** un cierre provisional se reabre en el mismo corte si un quórum mayor lo contradice dentro de 3 h; pasado ese plazo, o si lo
  confirmó el veedor, el cierre se respeta.

### ADR-089 — Vecino registrado, y reportar sigue siendo posible sin cuenta

**Estado: aceptada.**

Hay un rol nuevo, `VECINO`, con un único permiso (`GESTIONAR_PERFIL_PROPIO`). Se registra con correo, nombre, clave, **barrio** y
consentimiento (`POST /api/cuentas/vecino`); al confirmar el correo la cuenta queda **ACTIVA sin que nadie la apruebe**, porque solo
gestiona lo propio. Su sesión sirve en `/api/vecino/**` y ninguna ruta del panel con permiso: cada puerta de ingreso abre solo su puerta (una cuenta del panel no entra por
el ingreso de vecinos ni al revés, y ambos casos dan el mismo mensaje que una clave mala). Un vecino **nunca** obtiene permisos de panel: no
se invita, aprueba ni convierte entre vecino y panel, y lo comprueban tanto `PermisosEfectivos` como `Usuario`.

El vecino puede **verificar su barrio** (`POST /api/vecino/verificacion-barrio`): la ubicación del momento se compara con el polígono del
barrio que declaró (ADR-090). Un reporte suyo sale `CUENTA_VERIFICADA` si su barrio está verificado y reporta en él.

- **Gana:** cumple «debe haber registro» sin matar el alcance: **reportar sigue siendo posible sin cuenta** (ADR-007). La cuenta añade
  respaldo, un cupo mayor (5 reportes por barrio en 30 min frente a 3) y una vía para suspender a quien abuse.
- **Pierde:** hay dos identidades que reportan (cuenta y dispositivo) y el consenso tiene que tratarlas por igual. Una cuenta activa manda
  sobre un token de dispositivo para que una persona con sesión no cuente doble.
- **Registro uniforme (RNF024):** `202` exista o no el correo, con la misma duración mínima que el registro del panel.
- **Límites declarados:** no hay tolerancia de borde en la verificación (la contención del polígono es estricta); bloqueo de dispositivos,
  consentimiento/exportación/supresión y notificaciones (2.6–2.8) quedan fuera del corte mínimo; el texto legal de privacidad sigue en
  versión «borrador» (`aguavigia.privacidad.version`). Un token de vecino puede llamar a `GET /api/veedor/yo` y a
  `POST /api/veedor/sesion/cierre` porque solo muestran o cierran lo propio.
- **Se revierte** quitando el rol y dejando solo el token de dispositivo; no se recomienda: el registro es un requisito del proyecto.

### ADR-090 — La ubicación se usa y se descarta; la identidad del dispositivo la pone el servidor; el quórum pide composición

**Estado: aceptada.**

Tres decisiones que se sostienen entre sí:

1. **Ubicación transitoria.** La coordenada con la que un vecino verifica su barrio se usa y se descarta: no se guarda ni se audita (solo
   queda la marca `barrioVerificado` y su fecha). Una lectura peor que 200 m (ubicación aproximada por red) responde `422
   ubicacion-imprecisa` y no gasta intento; fuera de su barrio, `422 ubicacion-fuera-del-barrio`. Hay 3 intentos por día y cuenta, en Redis, y
   si Redis falla el cupo **falla abierto**. En un reporte, la coordenada se guarda redondeada a 3 decimales (~110 m). Sin `precisionMetros` la
   ubicación no verifica.
2. **Token de dispositivo firmado por el servidor.** `POST /api/dispositivos` devuelve `uuid.firma` (HMAC-SHA256); el secreto se genera y se
   persiste en `config_sistema`. La colección `dispositivos` guarda solo el SHA-256 del id y caduca por inactividad a los 365 días. La `huella`
   que elegía el cliente se **eliminó**: se ignora si llega. Sin identidad válida, `401 dispositivo-invalido`. Fabricar identidades cuesta una
   petición limitada (10 por hora por IP).
3. **Composición del quórum.** Alcanzar el umbral no basta: al menos un tercio del sustento (mínimo 1) debe tener alguna verificación y debe
   venir de al menos `aguavigia.consenso.redes-minimas` redes distintas (2 por defecto). La «red» es un HMAC de la IP que cambia cada día (hora
   de Cartagena), nunca la IP, y no sale por la API. Se aplica al formar el quórum, al fusionar con la memoria y al decidir si la memoria sigue
   sostenida.

- **Gana:** crear votos falsos ya no es gratis y el reporte anónimo sigue contando; no se guarda la casa de nadie; una ráfaga desde una sola
  red no mueve el mapa.
- **Pierde:** un tope duro por IP habría sido más simple pero el CGNAT móvil lo haría inalcanzable para gente legítima, por eso se compone en
  vez de topar. Una sala con un único WiFi no alcanza el quórum: en una presentación se baja `redes-minimas` a 1 **diciéndolo**.
- **Límites conocidos (auditoría de seguridad de F2):**
  - La ubicación y su precisión las declara el cliente y los polígonos de los barrios son públicos: quien forje una coordenada obtiene
    `UBICACION_VERIFICADA` sin estar allí. La verificación sube el costo de votar desde un barrio ajeno, no lo impide; por eso el quórum exige
    además redes distintas y no se presenta como prueba de identidad.
  - La «red» y el límite por IP usan la dirección que ve el servidor (`getRemoteAddr()`, nunca `X-Forwarded-For`, ADR-080). En IPv6 se usa
    solo el prefijo /64 (`RedDeOrigen`), porque un abonado recibe 2^64 direcciones. **Tras un proxy, o con Docker Desktop, todos los clientes
    comparten una IP**: el límite de 10 dispositivos por hora pasa a ser global y `redes-minimas=2` no se cumple nunca; en local o en una
    demostración se baja a 1 (`AGUAVIGIA_CONSENSO_REDES_MINIMAS=1`) **diciéndolo**. En producción haría falta un proxy de confianza.
  - Las cuentas se activan con solo confirmar el correo y no hay antigüedad mínima para votar, así que muchas cuentas o dispositivos desde dos
    redes pueden inflar un quórum de 3 a 15 votos. Un vecino puede además votar otra vez como dispositivo omitiendo el `Bearer`.
  - Quien registra una cuenta con el correo de otra persona puede dejarla pendiente con una clave que solo él conoce; si la víctima confirma el
    enlace, la cuenta queda activa con esa clave (*account pre-hijacking*). Pendiente de decidir; ver el resumen de F2.
- **Se revierte** devolviendo la huella al cliente; no se recomienda: reabre la fabricación gratuita de votos.
