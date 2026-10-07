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
| `docker compose up` **no siembra**; el sembrador está bajo el perfil `siembra` *(revertida por ADR-094: ahora siembra los sectores y el backend crea las cuentas sintéticas)* | Que levantar el sistema a mano parta de una base vacía y sembrar sea un paso explícito |
| Rutas **denegadas por defecto** en `SecurityConfig` | Un endpoint nuevo no queda público por descuido |
| Bloqueo de login por **cuenta y dirección**, con tope global por cuenta | Conocer un correo ya no basta para bloquear a su titular |
| La clave de las cuentas de demostración se genera en cada siembra | Una clave fija en un repo público era una puerta abierta |
| Guardar un corte, su bitácora y el estado de sus sectores es **una sola transacción** | Un fallo a medias dejaba el corte registrado con los sectores sin mover |
| El secreto TOTP sigue en claro en Mongo (riesgo aceptado) | Cifrarlo exige una clave persistente que rompe el arranque sin configuración; Mongo solo escucha en `127.0.0.1` |
| El historial de git no se reescribe | Obligaría a Yordy a volver a clonar; las claves expuestas se tratan como comprometidas y no se reutilizan |

## 4. Estado de un barrio, cierre de cortes e identidad (2026-10-01)

Decisiones de las fases F1 (ADR-087 y 088), F2 (ADR-089 y 090), F3 (ADR-091 a 093) y F4 (ADR-094) del plan de estados de barrio. Los números de ADR continúan los del registro histórico.

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

Hay un rol nuevo, `VECINO`, con un único permiso (`GESTIONAR_PERFIL_PROPIO`). Se registra con correo, nombre, **barrio** y
consentimiento (`POST /api/cuentas/vecino`) y **sin clave**: la elige quien abre el enlace del correo, igual que una invitación, y al
hacerlo la cuenta queda **ACTIVA sin que nadie la apruebe**, porque solo gestiona lo propio. Si la clave la pusiera quien rellena el
formulario, podría registrar el correo de otra persona con una clave suya y quedarse con la cuenta cuando ella confirmara el enlace
(*account pre-hijacking*, que la auditoría de seguridad de F2 encontró); sin conocer la clave, un registro ajeno no da nada. Su sesión sirve en `/api/vecino/**` y ninguna ruta del panel con permiso: cada puerta de ingreso abre solo su puerta (una cuenta del panel no entra por
el ingreso de vecinos ni al revés, y ambos casos dan el mismo mensaje que una clave mala). Un vecino **nunca** obtiene permisos de panel: no
se invita, aprueba ni convierte entre vecino y panel, y lo comprueban tanto `PermisosEfectivos` como `Usuario`. Al revés, el
permiso `GESTIONAR_PERFIL_PROPIO` es solo del vecino: ni el ADMIN lo hereda ni se concede a una cuenta del panel, de modo que ningún
controlador tiene que decidir quién cuenta como vecino.

El vecino puede **verificar su barrio** (`POST /api/vecino/verificacion-barrio`): la ubicación del momento se compara con el polígono del
barrio que declaró (ADR-090). Un reporte suyo sale `CUENTA_VERIFICADA` si su barrio está verificado y reporta en él.

- **Gana:** cumple «debe haber registro» sin matar el alcance: **reportar sigue siendo posible sin cuenta** (ADR-007). La cuenta añade
  respaldo, un cupo mayor (5 reportes por barrio en 30 min frente a 3) y una vía para suspender a quien abuse.
- **Pierde:** hay dos identidades que reportan (cuenta y dispositivo) y el consenso tiene que tratarlas por igual. Una cuenta activa manda
  sobre un token de dispositivo para que una persona con sesión no cuente doble.
- **Registro uniforme (RNF024):** `202` exista o no el correo, con la misma duración mínima que el registro del panel, ahora de
  400 ms (con 100 ms, un BCrypt lento sobre una CPU cargada habría pasado el piso y habría delatado la diferencia).
- **Escrituras sin pisarse:** el perfil y la verificación de barrio guardan solo si la cuenta sigue como se leyó
  (`UsuarioRepository.guardarSiNoCambio`); si otro la escribió entre medias —una suspensión del administrador, por ejemplo—
  responden 409 y no la deshacen. No se usó `@Version`: un documento antiguo sin ese campo se intentaría insertar y fallaría.
- **Privacidad de la auditoría:** lo que se hace sobre la cuenta de un vecino guarda solo el bloque de red de la IP (/24 en IPv4 y
  /64 en IPv6) y se borra solo a los 180 días (`aguavigia.retencion.auditoria-vecinos-dias`, índice TTL sobre `venceEn`). La
  auditoría del panel se conserva completa y sin vencimiento: es la evidencia de quién hizo qué. Los logs enmascaran el correo.
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
  - Quien registra una cuenta con el correo de otra persona la deja sin clave, pero con **su** nombre, barrio y casilla de avisos; la
    persona los ve al elegir su clave y puede cambiarlos desde el perfil. Los consentimientos toman la fecha en que ella actúa.
  - `HuellaDispositivo.deCuenta` se deriva solo del id de usuario: un ADMIN que lo conozca puede enlazar los reportes de esa cuenta. Es
    lo que permite que una cuenta vote como una persona; se acepta y queda documentado.
- **Se revierte** devolviendo la huella al cliente; no se recomienda: reabre la fabricación gratuita de votos.


### ADR-091 — La foto de un reporte es evidencia, no contenido: token de subida, moderación y una sola ruta

**Estado: aceptada.**

Hasta F2 cualquiera que conociera el id de un reporte —los ids son públicos en `/api/bitacora/{id}/sustento`— podía adjuntarle una
foto, y todo el directorio de fotos se servía en `/fotos/**` sin mirar la moderación. Ahora:

1. **Token de subida de un solo uso.** `POST /api/reportes` devuelve `subidaToken`; `POST /api/reportes/{id}/foto` lo exige en la
   cabecera `X-Subida`. Está atado a ese reporte, vence a los 10 minutos (`aguavigia.reportes.vigencia-subida-minutos`) y se gasta al
   usarlo. Solo se guarda su SHA-256 (colección `subidas_foto`, con TTL por `venceEn`); pedir otro invalida el anterior. Falta, ya usado,
   vencido o ajeno responden lo mismo (`403 subida-no-autorizada`), también si el reporte no existe: distinguirlos diría qué ids hay.
   El archivo se valida **antes** de gastar el token, para que una foto rechazada no deje al autor sin reintento.
2. **Formatos.** Solo JPEG y PNG, y se comprueba la firma del archivo, no solo el tipo declarado. WebP se rechaza con `415
   formato-no-permitido` (antes salía `400`): el JDK no lo decodifica, así que no se puede recomprimir ni quitarle el EXIF. El servidor
   recodifica la imagen, guarda su SHA-256 en el reporte y el nombre del archivo es un UUID que genera él.
3. **Una sola ruta, con estado.** `/fotos/**` desaparece. `GET /api/fotos/{nombre}` (sin sesión) responde solo si el reporte dueño de la
   foto está APROBADO y la foto no se descartó; cualquier otro caso —no existe, pendiente, descartada— es el **mismo 404**, para que nadie
   pueda sondear qué fotos hay. El veedor la ve siempre por `GET /api/veedor/fotos/{nombre}` (`VER_PANEL`), y
   `PATCH /api/veedor/reportes/{id}/foto/descartar` (`MODERAR_REPORTES`) retira solo la foto sin tocar el reporte ni su voto. Las respuestas
   llevan `X-Content-Type-Options: nosniff`; la pública se cachea un minuto y la del panel no se cachea.
4. **La foto nunca vota.** Es evidencia para quien modera; el quórum se sigue formando con los reportes.
5. **Estado de la foto.** Los reportes y la cola de moderación exponen `fotoEstado` (`SIN_FOTO`, `EN_REVISION`, `PUBLICA`, `DESCARTADA`) para que
   la interfaz muestre «en revisión» en vez de pedir una imagen que daría 404. Un reporte anterior a F3 con la URL vieja `/fotos/x.jpg`
   sale con la ruta nueva.
6. **Límite por IP**: 10 subidas por 10 minutos (`/api/reportes/*/foto`), además del de `/api/reportes/**`. Es un tope por IP, así que
   con Docker Desktop (una sola IP para todos los clientes) es global; se dice en `errores-y-limites.md`.

- **Gana:** ocupar la foto de otro exige su token; nada se ve sin moderar; la ruta pública ya no expone el directorio.
- **Defensas (auditoría de seguridad de F3):** las dimensiones se leen de la cabecera y más de 25 megapíxeles se rechaza sin decodificar (una PNG de un
  color pesa KB y declara miles de millones de píxeles: decodificarla tumbaba la JVM); una PNG que sigue pesando más de 3 MB tras procesarse se rechaza; la
  foto se asigna con una escritura atómica de sus campos y solo si el reporte aún no tiene (no pisa una aprobación o una confirmación simultáneas); si el procesado
  falla tras gastar el token, se devuelve por 2 minutos para reintentar; leer fotos tiene tope por IP (120 por minuto) y se busca por igualdad exacta.
- **Pierde:** quien pierda la respuesta de `POST /api/reportes` pierde también el token y no puede subir su foto (se acepta: la foto es
  opcional). Un reporte aprobado no sube su foto más tarde. Una foto aprobada que se descarta puede seguir en una caché compartida hasta un
  minuto.
- **Retención:** las fotos se conservan según `retencion-evidencia`; D19 propone 180 días, pero esa tarea sigue desactivada por
  defecto y no se tocó aquí.
- **Se revierte** devolviendo el manejador estático de `/fotos/**`; no se recomienda: reabre la ocupación de fotos ajenas y la exposición
  sin moderar.

### ADR-092 — Acuacar se publica solo únicamente si la lectura es fiable y tiene sentido; el histórico es historia

**Estado: aceptada.**

Hasta F3 el pipeline publicaba **todo** lo oficial sin mirar la confianza: una mención suelta de 0,45 pintaba barrios. Y leía un solo
horario por boletín, aunque los de Acuacar traen uno por zona («Grupo 1 desde las 11:00… Grupo 2 desde las 8:00…»).

1. **Una zona, un aviso.** `HeuristicaExtractor.extraerPorZonas` devuelve un evento por zona con su horario y solo sus barrios (también cuando
   el horario trae la fecha en cada extremo, «de 8:00 a 16:00 horas», «12:00 de la medianoche» o «del mismo día»). Una ventana global que
   luego se detalla por días no es una zona. Las 13 fixtures de boletines reales de `ingesta-local/` fijan el comportamiento.
2. **Restablecimiento frente a suspensión.** Un aviso es de restablecimiento si lo dice («restablecimiento progresivo del servicio») y **no
   anuncia una suspensión nueva** (ventana o «se programó…»): mencionar la suspensión que termina no lo vuelve un corte, y un anuncio que
   habla de «restablecer las condiciones» tampoco es un restablecimiento. «Se aplaza»/«se cancela» sin horario nuevo es un
   `AVISO_DE_ANULACION`: se reconoce y no se publica como corte.
3. **Compuertas (D5).** `CompuertaDePublicacion` decide por **aviso** (la zona entera, no sector por sector): confianza ≥ 0,85 (≥ 0,75 si es un restablecimiento, que no
   inventa una emergencia), ventana ≤ 72 h, inicio a ≤ 7 días de la publicación, ≤ 40 barrios y sin nombres ambiguos (dos barrios del catálogo con el
   mismo nombre se apartan en vez de asignarse a uno cualquiera). Lo que no pasa se guarda como propuesta pendiente con su
   `motivoDeRevision`. La prensa siempre espera. Los umbrales son valores iniciales (`aguavigia.ingesta.compuertas.*`).
4. **El histórico es historia (D27).** Un corte cuya ventana terminó hace más del plazo de expiración (72 h) se guarda al aprobarse como `EXPIRADO`, con un
   evento `CORTE_EXPIRADO` con la fecha del hecho, sin recalcular el barrio: no mueve el mapa ni notifica. Y un boletín oficial de
   restablecimiento deja de fijar `CON_SERVICIO` pasado ese plazo (sigue cerrando la ventana que cubre): uno de hace meses dice qué pasó entonces, no hoy.
- **Gana:** una lectura dudosa o desbordada no llega al mapa sola; el primer ciclo con 350 boletines no deja cientos de cortes «abiertos» ni cambia el mapa.
- **Pierde:** la cola del veedor crecerá con los boletines largos (más de 40 barrios coinciden con los de las paradas técnicas); los umbrales sin datos reales pueden
  quedar estrechos o anchos y se calibran en F6. Un aviso de aplazamiento aún no anula el corte anunciado: lo hace el veedor por la API de anulación.
- **Defensas (auditoría de seguridad de F3):** el texto de un documento tiene tope (150 000 caracteres; un boletín real pasa poco de 12 000) antes de pasar
  por las expresiones regulares; el tope de «demasiados barrios» cuenta el boletín entero y no cada zona; una propuesta sin cita textual no se publica sola; un aviso
  que promete «interrumpir» y luego «restablecer gradualmente» no se lee como restablecimiento; `urlOriginal` e `imagenUrl` deben ser http(s) (una fuente hostil no
  cuela `javascript:`).
- **Límites conocidos, sin resolver:** (1) la «confianza» es estructural (hay lista de barrios y horario), no una medida de acierto: la compuerta reduce el daño, no lo
  elimina; (2) el inicio de la ventana puede quedar un día corrido si el boletín trae una fecha de encabezado antes de la fecha del corte, y el margen de 7 días no lo
  ve (se mantiene en 7 porque las paradas técnicas se anuncian con 4 días); (3) no hay cuota total de disco para las fotos ni se sirven en streaming; (4) descartar una foto
  no deja rastro en la auditoría (tampoco aprobar o descartar un reporte); (5) el token del enlace de restablecimiento va en la URL (query) y vale 24 h, no es de un
  solo uso: el frontend debe quitarlo de la barra con `history.replaceState`, y el cupo por suscripción (3 votos por barrio cada 30 min, uno por huella en el quórum) limita el abuso;
  (6) un aviso dudoso en cola bloquea a uno posterior del mismo barrio y estado hasta que el veedor decida (queda en el log).
- **Límites:** el extractor sigue siendo heurístico, y el nombre de un barrio solo casa si existe en el catastro; las urbanizaciones y conjuntos quedan sin reconocer (se anotan en el log).
  Los «sectores parciales» (manzanas) se publican como el barrio entero: es el límite declarado de afectación parcial.

### ADR-093 — «¿Ya volvió el agua?» con un toque, y por qué no hay cierres reales desde los boletines

**Estado: aceptada.**

1. **Enlace de un toque (D18).** Se reporta el problema, no la solución, así que confirmar un restablecimiento es lo que menos llega. El correo de aviso de un barrio sin
   servicio o con presión baja lleva un enlace **firmado** (HMAC-SHA256, secreto propio distinto del de dispositivos) con barrio, suscripción y vencimiento de 24 h. La
   pantalla es del frontend y llama a `POST /api/sectores/{id}/restablecimiento?token=`: es un POST para que abrir el correo —o que un cliente lo previsualice— no vote.
   Registra un `SERVICIO_RESTABLECIDO` por el mismo camino de cualquier reporte (cupo, quórum, resolutor); la huella es la de la suscripción, con prefijo propio para que no se confunda con
   una cuenta o un dispositivo. Un enlace de otro barrio, vencido o de una suscripción cancelada o sin confirmar da el mismo `403 enlace-invalido`.
   Un vecino registrado confirma desde la app con `POST /api/reportes`. Telegram y push quedan fuera (2.8).
2. **Cierres reales desde boletines (D30): no se construyen.** Se revisó el histórico en vivo con **una** petición (robots.txt sin bloqueos; User-Agent propio con
   `alertas@aguavigia.com`; `/wp-json/wp/v2/posts`, `per_page=100`, 352 boletines en total): en los **100 más recientes (13/05 a 01/10/2026)**, 14 mencionan un
   restablecimiento y solo 2 enumeran barrios, y esos 2 son partes de avance de una reparación, no avisos de servicio restablecido. No hay con qué cerrar un corte barrio por
   barrio sin inventar. **Límite de la revisión:** solo se miró esa página de 100; los 252 anteriores no. El Índice de Cumplimiento se muestra «sin datos suficientes» hasta que
   haya cierres reales (del veedor o de los vecinos).
- **Gana:** el voto de restablecimiento cuesta un toque; nada se inventa.
- **Pierde:** una suscripción por correo es una identidad barata (cualquiera puede suscribir correos propios); por eso el quórum sigue exigiendo composición y el cupo por barrio sigue valiendo. Quien
  reciba el correo reenviado puede votar con el enlace hasta que venza.

### ADR-094 — Un solo `docker compose up`: lo único inventado son las cuentas sintéticas, y se declaran

**Estado: aceptada.** Revierte la decisión del 29/09 («`docker compose up` no siembra») y retira del arranque los escenarios inventados.

1. **Qué deja listo el arranque.** El `sembrador` ya no está bajo un perfil: siembra los 211 barrios del catastro y espera a que el backend termine de
   crear las cuentas sintéticas. **No hay reportes, cortes ni estados inventados**: el mapa muestra lo que dicen los boletines de Acuacar y lo que reporten
   vecinos de verdad. `sembrar-demo.mjs` (reportes por la API), `sembrar-historico-cortes.mjs` y `sembrar-usuarios-demo.mjs` siguen en `scripts/` para
   quien los pida a mano, marcados como fuera del arranque. Es idempotente: repetir `up` no duplica nada.
2. **Cuentas sintéticas (D20, D36).** Las crea el propio backend (`ImportadorDeVecinosSinteticos`, `aguavigia.siembra.vecinos-sinteticos`; 30 000 en el perfil
   `docker`, 0 en el resto) y no un script que escriba en Mongo: así pasan por las mismas reglas de alta de un vecino y el esquema no puede divergir. Esperan a que
   exista el ADMIN inicial (que solo se crea si no hay ninguna cuenta) y a que existan los 211 sectores. **Decisión sobre el consentimiento:** `RegistrarVecino`
   exige consentimiento de privacidad y D20 prohíbe fingir uno que nadie dio, así que las sintéticas usan una variante de sistema de la fábrica de `Usuario`
   (`sinteticoComoVecino`): misma validación de barrio, correo y esquema, **sin** correo enviado, consentimiento ni barrio verificado. Entran ACTIVAS por decisión del
   sistema, con la auditoría `CUENTA_SINTETICA_ACTIVADA` (un evento por lote de 1 000, no por cuenta: 30 000 eventos serían 30 000 escrituras para anotar lo mismo),
   con correo de un dominio reservado (`.invalid`), `datosDeDemostracion: true` y `origen: SEMBRADO`. Comparten un solo hash BCrypt de una contraseña aleatoria que se
   descarta: **nadie puede iniciar sesión con ellas**. El reparto por barrio es proporcional a la población y determinista (la cuenta N cae siempre en el mismo barrio),
   así que completar una pasada interrumpida produce las mismas cuentas. **La afirmación pública exacta** es «cuentas sintéticas generadas por el sistema con las reglas
   de alta de un vecino»; nunca «30 000 personas se registraron». Los **3 usuarios de panel sintéticos** del plan (D22) **no se crean**: sin credenciales conocidas no
   sirven, y dárselas fabricando un segundo factor sería inventar; las cuentas del panel las crea el ADMIN por invitación.
3. **`GET /api/sistema/modo`** → `{modo: REAL|SIMULACION, cuentasSinteticas}` (D26/D32): la interfaz muestra el banner de la simulación y la nota de las cuentas sintéticas.
   El conteo se recuerda un minuto: contar 30 000 documentos en cada visita a la página sería un coste sin sentido.
4. **El Índice de Cumplimiento por par corte-barrio, con su calidad (D14).** Cada barrio de un corte se mide con su propio cierre. El Índice declara `porcentajeProvisional`,
   `cortesSinCierreConfirmado` (incluye los `EXPIRADO`) y `cortesAnulados`, y `GET /api/cumplimiento/calidad` responde aunque no haya un solo cierre (cuando el Índice da
   `400`), para decir «sin datos suficientes» **con cifras**. Los cortes sin cierre no cuentan a favor ni en contra de Acuacar: se declaran. Sin esto, el Índice premiaba a quien
   deja los cortes abiertos (el sesgo de supervivencia que D14 nombra).
5. **`senalRed` en la cola de moderación (D9).** Una red (resumen diario de la IP) que envió al menos `aguavigia.moderacion.rafaga-minima` (5) reportes a un barrio en
   `rafaga-ventana-minutos` (30) marca los reportes pendientes que vengan de ella. **No bloquea nada**: un tope duro por IP haría inalcanzable un quórum legítimo detrás
   de un CGNAT o de una sala con un WiFi; es una señal para que el veedor mire primero. Cuenta también lo ya moderado (descartar un reporte de la ráfaga no la borra) y
   se calcula con una agregación por página, no por reporte.
6. **El contador de Redis se repuebla al arrancar (D29).** La ventana del quórum vive en Redis y es un prefiltro: con Redis vaciado o tras un reinicio el primer reporte nuevo
   no llegaba al listón aunque los demás votos estuvieran en Mongo. Al arrancar, y antes de recalcular los barrios, se devuelven a la ventana los votos recientes de Mongo con su
   instante original (`ZADD NX`: un voto que ya estaba no se pisa). Un fallo de Redis aquí se registra y no impide la puesta al día.
7. **`INGESTA_MODO=auto` (D12).** Acuacar en vivo y, si esa consulta falla (sin red, sitio caído), los 13 boletines reales guardados en el repositorio como respaldo. Nunca
   inventa uno. El fallo en vivo **sigue visible** en el panel (el respaldo no esconde la caída) y la lectura de respaldo **no avanza la marca de Acuacar**: si avanzara con los
   13 boletines guardados, al volver la red solo se leería lo posterior y se perdería el histórico completo.
   **Dos fallos que solo aparecieron al levantar el arranque único de verdad** (en un proyecto de compose aparte, no en la instancia real): el backend
   corre su primer ciclo de ingesta *antes* de que el sembrador cargue los barrios, y con el catálogo vacío ningún nombre se reconocía, así que **todo el
   histórico de Acuacar se descartaba y la marca avanzaba**: se perdía para siempre. Ahora un ciclo sin barrios no procesa nada ni mueve la marca, y el primer
   ciclo espera un minuto (`aguavigia.ingesta.retraso-inicial-ms`) para que lo normal sea encontrar los barrios ya sembrados.
8. **Concesiones del entorno local, dichas.** El compose arranca con `AGUAVIGIA_CONSENSO_REDES_MINIMAS=1`: con Docker Desktop todos los clientes del equipo llegan con la misma IP, y
   con el valor 2 el quórum nunca se cumpliría (ADR-090). Es del entorno local, no del producto. `RATE_LIMIT_FACTOR` (por defecto 1, nunca aprieta) multiplica los topes por IP para
   las instancias de simulación y de carga, que crean cientos de vecinos desde un solo equipo; sin él, los 10 dispositivos por hora de la instancia real lo impedirían.
9. **Los scripts que mandaban `huella`** (`sembrar-demo`, `verificar-flujos` y los de `scripts/carga`) pasan a `X-Dispositivo`, con una coordenada dentro del barrio y `precisionMetros`
   para que el voto cuente como verificado. Comparten `scripts/lib/identidad-api.mjs`.
- **Gana:** un arranque que deja todo listo sin inventar el acueducto; lo sintético tiene una sola puerta de entrada, declarada y auditada; el Índice dice cuánto sostiene cada número.
- **Pierde:** 30 000 cuentas en `usuarios` ensucian el listado del administrador (por eso `sintetica` en `UsuarioRespuesta`); el importador inserta en lotes en segundo plano, así que justo
  después de arrancar aún pueden faltar (el sembrador espera a que terminen, hasta 5 minutos).
- **Límites conocidos:** una ráfaga legítima (una avería en una zona con CGNAT) también activa `senalRed`: es una señal, no una condena; los umbrales de ráfaga son valores iniciales
  que se calibran en F6; las cuentas sintéticas no ejercen el registro por HTTP (eso lo hace la simulación, F5).
- **Se revierte** devolviendo el perfil `siembra` al sembrador y dejando `vecinos-sinteticos=0`; no se recomienda: reabre la siembra de escenarios inventados.

### ADR-095 — Cierre de F4: una cuenta sintética no tiene titular, y lo que no es de Sebastián no entra en el contenedor

**Estado: aceptada.** Cierra los hallazgos de la revisión de seguridad y de dominio de F4.

1. **La cuenta sintética se trata como un correo inexistente (B1).** Las 30 000 cuentas comparten una clave precalculada y su correo es de un dominio reservado (`.invalid`): nadie
   es su titular. `Usuario.esSintetica()` (`datosDeDemostracion` y rol `VECINO`) es ahora el criterio único. Con una sintética, `AutenticarUsuarioService` gasta el mismo tiempo que un BCrypt,
   cuenta el intento fallido y responde «Correo o clave incorrectos.» **sin comparar la clave y sin auditar**: auditar cada intento dejaría llenar la auditoría desde fuera con 30 000 correos
   predecibles, y comparar la clave dejaría entrar a quien acertara la compartida. `RestablecerClaveService.solicitar` no emite token ni enlace para ellas (sería una vía para apropiarse de la
   cuenta) y los dos registros duplicados (`RegistrarVecinoService` y `RegistrarUsuarioService`) no avisan a un titular que no existe. Las cuentas de panel que siembra
   `sembrar-usuarios-demo.mjs` (VEEDOR/OBSERVADOR con `datosDeDemostracion` y claves reales) **no** son sintéticas y siguen pudiendo entrar y restablecer su clave: hay un test para cada caso.
2. **`origen: SEMBRADO` y el conteo siguen el mismo criterio (B2).** `UsuarioMongoAdapter` marca `SEMBRADO` solo si `esSintetica()`, y `contarSinteticas` cuenta `rol=VECINO` y
   `origen=SEMBRADO`. Antes cualquier `datosDeDemostracion` se marcaba `SEMBRADO`: una cuenta de panel de demostración habría inflado la bandera pública `cuentasSinteticas` y el script
   `sembrar-usuarios-demo.mjs` la habría tratado como suya. Ese script borra ahora de `usuarios` solo `{datosDeDemostracion: true, origen: {$ne: 'SEMBRADO'}}`, así que no puede llevarse
   las sintéticas del backend cuando `scripts/carga/demo.mjs` lo ejecuta por haber menos de 30 000 cuentas.
3. **La prueba de carga se niega a correr sobre datos reales (B3).** `scripts/lib/datos-reales.mjs` cuenta reportes, cortes, propuestas de ingesta y cuentas que no son de demostración (excepto el
   ADMIN inicial, que existe siempre). Si hay alguno, `scripts/carga/demo.mjs` termina con un mensaje que dice qué encontró y qué arriesgan `mongorestore --drop` y el perfil `carga`; solo sigue con
   `--sobre-datos-reales`. Efecto conocido: tras una carga sin `--restaurar`, los reportes sintéticos que dejó cuentan como reales y la siguiente exige el flag.
4. **`.env` ya no entra entero en los contenedores (B4).** `env_file: .env` se quitó de `mongo` y de `backend`: `GITHUB_PERSONAL_ACCESS_TOKEN` (y cualquier otra cosa de `.env`) llegaba al backend. El backend
   recibe una lista explícita, **sin valor** (`ADMIN_INICIAL_CORREO`, `VEEDOR_PASSWORD_HASH`, `JWT_SECRET`, `IOT_KEY`, `TELEGRAM_BOT_TOKEN`, `INGESTA_INTERVALO_MS`, `INGESTA_RETRASO_INICIAL_MS`,
   `RATE_LIMIT_FACTOR_CUENTAS`, `AGUAVIGIA_MODO`): si está en `.env` llega, y si no, no existe y Spring usa su valor por defecto, porque un valor vacío **no** equivale a ausente. Comprobado con
   `docker compose config` sobre un `.env` de ejemplo. `CORREO_CUENTAS_HABILITADO` no se lista: `docker-compose.carga.yml` ya la interpola hacia `AGUAVIGIA_CORREO_CUENTAS_HABILITADO`.
5. **Un mínimo de redes menor que 1 no arranca (B5).** `RecalcularSectorService` lanza `IllegalArgumentException` con `redes-minimas < 1` (el quórum no exigiría diversidad) y `CasosDeUsoConfig`
   avisa en el log con `< 2`, que es la concesión del entorno local de ADR-090.
6. **Ya cerrado en la ronda anterior, para que quede escrito:** `RATE_LIMIT_FACTOR` solo mueve las rutas que no son de cuentas y `RATE_LIMIT_FACTOR_CUENTAS` (aparte, tope 1000) las de ingreso, segundo
   factor, cambio de clave, altas y correos, de modo que subir el factor de una prueba de carga no afloja la defensa contra la fuerza bruta ni contra la inundación de correos; `/api/sistema/modo` tiene su
   rate limit y `ConsultarModoDelSistemaService` renueva el conteo con `tryLock` (una sola consulta a Mongo aunque entren muchas visitas a la vez).
7. **Dependencias (Trivy).** `jackson-bom` pasa a 2.21.7 (cierra los cuatro HIGH de jackson-core y jackson-databind). CVE-2026-47884 (spring-webmvc 6.2.19, ejecución remota en `XsltView`) se acepta con
   `.trivyignore` y fecha de revisión 2026-12-31: el backend no tiene vistas (todos sus controladores son `@RestController`, sin ViewResolver ni plantillas), así que el código vulnerable no es alcanzable;
   la corrección exige Spring Framework 7.0.9 (Spring Boot 4) y queda como **deuda anotada**.
- **Gana:** las cuentas sintéticas no abren una puerta de entrada, ni de avisos, ni de recuperación; el sembrador y la bandera pública hablan de lo mismo; las pruebas de carga ya no destruyen sin avisar.
- **Pierde:** una cuenta sintética no puede reclamarse nunca (no hay titular a quien devolverla); el flag `--sobre-datos-reales` es una barrera pequeña pero deliberada.
- **No se hizo, a propósito:** observar los barrios con `executePipelined` y comprobar los sectores antes de los colectores: rompe pruebas y no compensa.

### ADR-096 — La simulación: un día acelerado que es también prueba de aceptación, y lo que destapó

**Estado: aceptada.** Cierra F5 (§12 del plan). Una segunda instancia del backend (`backend-sim`: puerto 8082, base `aguavigia_sim`, Redis db 1, `INGESTA_MODO=simulacion`)
corre un guion de un día de Cartagena a velocidad x1/x10/x60/x300 por la **API real** (300 vecinos que se registran, verifican correo y barrio, reportan con foto; un veedor que modera;
boletines que entran por el mismo extractor) y, al final de cada acto, **asierta** lo que debió pasar. Cada decisión de abajo es mía (delegada) y se puede revertir una a una.

1. **El reloj acelerado se re-fija en cada tick y se congela mientras algo se ejecuta.** Entre dos peticiones al reloj (`POST /api/sim/reloj`) el backend corre a 1x, así que el ejecutor lo
   vuelve a poner donde está el cronómetro cada 250 ms (y también mientras una aserción espera). Mientras corre una acción o las aserciones de un acto el cronómetro **se congela**: registrar 300 vecinos
   por HTTP tarda lo que tarda y, sin el congelado, a x300 el guion se adelantaría a sí mismo. Comprobado a x300 (≈3 min reales) y con pausa, cambio de velocidad y salto en mitad de la corrida.
2. **El JWT caduca con el reloj acelerado y eso es correcto: el simulador renueva sesiones.** `JwtProvider` usa `RelojPort` (TTL de 8 h): a las 16:00 simuladas las sesiones iniciadas a las 08:00 ya habían
   caducado, y el 401 llegaba disfrazado de «falta la identidad del dispositivo». El ejecutor renueva las del panel cada 3 h simuladas y tras cada salto; cada vecino renueva la suya antes de reportar si
   tiene más de 3 h. Es lo que haría una persona. No se tocó el TTL del backend.
3. **`/api/sim/**` solo existe en la instancia de simulación.** Sin `aguavigia.sim.habilitada=true` el controlador no se registra: **404** (probado con un contenedor de la imagen nueva y configuración real;
   y el compose real no lleva ninguna variable de simulación). Con la propiedad: **503** si la clave está vacía, **401** si falta o no coincide (comparación en tiempo constante) y el arranque exige 32
   caracteres o más. `POST /api/sim/sesion-admin` abre una sesión de ADMIN **sin segundo factor** —es lo más sensible de la fase— y por eso solo existe aquí, audita `SESION_INICIADA` «por la ruta de
   simulación» y nunca se declara en el compose real. `X-Sim-Key` no aparece en ningún log (0 apariciones de la clave en el log tras una corrida completa). La clave se genera con
   `scripts/simulacion/preparar.mjs` en `.env` (ignorado por git), nunca se imprime ni se sobrescribe, y en CI se genera en cada ejecución.
4. **`reiniciar` no suelta la base entera.** El backend crea los índices y la cuenta ADMIN solo al arrancar: soltar `aguavigia_sim` dejaría la instancia sin ADMIN y sin el `2dsphere` hasta reiniciar el
   contenedor. Borra los documentos de todas las colecciones (menos el ADMIN), vacía Redis db 1, devuelve el reloj al real, vacía el buzón de boletines y vuelve a sembrar los sectores. Se niega si la base
   no acaba en `_sim` o si Redis es la db 0. **Vaciar la caché de Redis después de sembrar** no es un detalle: el backend guarda la lista de sectores y, si la leyó vacía antes de la siembra, la sirve vacía.
   **Mailhog es el mismo** que usa la instancia real, así que no se vacía entero: solo se borran los correos dirigidos a `sim.aguavigia.test`.
5. **El guion es la prueba de aceptación y corre en CI** (`.github/workflows/simulacion-ci.yml`): 54 aserciones, código de salida distinto de 0 si falla una aserción o una acción. Si una **acción** falla el guion
   se aborta (lo siguiente depende de ella); si falla una aserción sigue (y `--detener-en-fallo` lo detiene).
6. **Un aviso de «se aplaza» se reconoce, no anula.** El plan hablaba de una propuesta automática de anulación para la cola. Casar un aplazamiento con el corte que aplaza exige una heurística de barrios y
   fecha que hoy no existe y puede equivocarse; el sistema solo lo reconoce y **el veedor decide** (el guion usa la anulación del veedor). Un error aquí anularía un corte vigente, así que no se automatiza.
7. **Límites que se dicen, no se esconden.** Desde un solo equipo no hay «redes distintas»: `backend-sim` baja `AGUAVIGIA_CONSENSO_REDES_MINIMAS` a 1 (la real exige 2; ver ADR-090 y B5). No existe la colección
   `notificaciones` ni el tope de 15 min (2.8, fuera de alcance), así que no se asierta `OMITIDA_POR_TOPE`: se asierta que los suscriptores reciben su aviso por correo. Los **sensores IoT** están construidos
   pero inactivos y **no forman parte** de la simulación ni del guion (se omite el acto «El Pozón» de §12). Los topes de ritmo (`RATE_LIMIT_FACTOR` y `RATE_LIMIT_FACTOR_CUENTAS`, ×1000) están subidos solo en esta instancia.
8. **Dónde el guion discrepa del plan, y por qué.** (a) La reapertura de un corte (D15) solo vale para un cierre **provisional**; lo que confirmó un veedor es definitivo. El plan ponía a Nelson Mandela a
   confirmar y luego a reabrirse: ahora la reapertura se prueba en **San Fernando** (cierre provisional de vecinos → 15 «sin agua» → el mismo corte se reabre; el estado vuelve a `SIN_SERVICIO` con origen
   ACUACAR porque lo sostiene el corte oficial reabierto, los vecinos solo lo disparan) y en Nelson Mandela se prueba lo contrario: tras el cierre confirmado, el quórum nuevo vuelve a poner el mapa en
   «sin servicio» con origen VECINOS **sin** reabrir el corte ni perder la duración medida. (b) `estadisticas` cuenta **avisos aprobados** de Acuacar por barrio (ver su comentario), no cortes: anular un corte no
   las cambia. Lo que sí sale al anular es el Índice, y es lo que se asierta (`indice-excluye-anulados`).

**Lo que la simulación destapó (todo con test propio):**

- **Bug de producción — un boletín con ventana futura no llegaba nunca al mapa.** Un corte anunciado para más tarde (el caso normal) llegaba como `CORTE_PROGRAMADO` y `RecalcularSectorService` lo convertía en una
  `VentanaOficial` que lo rechazaba («una ventana solo declara un corte o una baja de presión»): el boletín caía en la cola de fallidos. Los 13 boletines locales son pasados, por eso nunca saltó. Se normaliza
  `CORTE_PROGRAMADO` → `SIN_SERVICIO` como estado de la ventana.
- **Bug de producción — el Índice descartaba un corte expirado entero.** El corte de Manga, Nelson Mandela y San Fernando expira porque San Fernando nunca se cerró, y con él se iban las duraciones medidas de
  Manga (70 min) y Nelson Mandela: `GET /api/cumplimiento/sectores/manga` daba 400. El Índice es por par corte-barrio (D14): un expirado aporta los barrios que sí se cerraron.
- **Planificadores con bloqueo mínimo fijo.** El barrido de ventanas (30 s) y la puesta al día (1 min, la que expira cortes) pedían a `EjecucionUnica` un mínimo fijo mayor que el intervalo configurado, así que `5000` ms
  no aceleraba nada. El mínimo ahora es `min(fijo, intervalo)`. `backend-sim` pone ambos intervalos en 5 s.
- **`CasosDeUsoConfig` registra solos todos los `application/*Service`:** los cuatro de simulación recibían configuración y rompían el arranque de la instancia real; ahora están en su `excludeFilters`.
- **Plantillas del simulador fijadas contra el extractor real** (`PlantillasDeSimulacionTest`): el aviso «SIMULACIÓN» tiene que empezar por «Aguas de Cartagena» (marcador de fin de enumeración) o su primera frase
  entra como barrio; la mención suelta necesita «barrio X» en prosa con una palabra de suspensión.
**Revisión de seguridad y de dominio de F5 (hallazgos corregidos y límites aceptados):**

- *Corregido:* `vaciarRedis` mandaba `SELECT` y `FLUSHDB` en una sola escritura y la guardia solo comparaba `db === 0`: con `REDIS_DB=abc` (NaN) el `SELECT` fallaba y el `FLUSHDB` caía en la base 0, la de la
  instancia real. Ahora la base debe ser un entero entre 1 y 15 y el `FLUSHDB` espera el `+OK` del `SELECT`. `reiniciar` también exige `modo=SIMULACION` en la API, como `iniciar`.
- *Corregido:* `sim.habilitada=true` ahora falla al arrancar si la instancia no se declara `AGUAVIGIA_MODO=SIMULACION` (la sesión de ADMIN sin 2FA no puede depender de una sola propiedad), y
  `INGESTA_MODO=simulacion` falla si la simulación no está habilitada (dejaría la ingesta real muda). `ColectorSimulado` ya no filtra por fecha: la marca solo avanza y descartaba en silencio un boletín
  si el reloj retrocedía. El reloj valida duración positiva y un instante a menos de 366 días de la hora real, y sus tres operaciones son `synchronized`. El workflow declara `permissions: contents: read`.
- *Aceptado y dicho:* (a) **el canal Pub/Sub de SSE es global al Redis**: `backend-sim` publica en el mismo canal que la instancia real, que solo avisa a sus clientes de que vuelvan a pedir
  `/api/sectores` (sus propios datos): ruido, no fuga. *(Corregido en ADR-097: el canal lleva sufijo por modo.)* (b) **Sin límite de peticiones sobre `/api/sim/**`**: la clave de `preparar.mjs` tiene 256 bits y la guardia exige 32 caracteres, pero una escrita a mano
  podría ser débil: úsese `preparar.mjs`. (c) `GuardiaDeSimulacion.exigir()` se llama a mano en cada método de `SimController`; un endpoint nuevo en `/api/sim/**` tendría que llamarla (hoy lo hacen los 5).
  (d) La auditoría del ingreso por simulación es `SESION_INICIADA` con texto libre, sin acción propia. (e) `SimController` deriva el id de un boletín sin id a partir de su contenido: es forma, no negocio.
- **Gana:** una prueba de punta a punta que cualquiera puede ver (y repetir en CI) y que ya encontró dos bugs que 1 961 pruebas no veían. **Pierde:** la simulación no cubre sensores, tope de avisos ni redes
  distintas, y una corrida completa tarda ≈3 min a x300 y necesita Docker.

### ADR-097 — Cierre de F6: la carga se midió en un proyecto aislado, las métricas solo sirven para calibrar, y lo que dijeron las revisiones

**Estado: aceptada.** Cierra F6 (§13 del plan). Cada decisión es mía (delegada) y se puede revertir una a una.

**1. Carga: qué se midió, cómo y con qué límites (2026-10-06).**
- **Método aislado.** La medición corrió en un proyecto Docker propio (`-p aguavigia-simtest`: contenedores `simtest-*`, puertos 47017/46379/48082…, base y Redis desechables) y **nunca contra la instancia real**.
  No se usó `scripts/carga/demo.mjs` porque tiene nombres de contenedor fijos y chocaría con la instancia real. El generador es k6 con `scripts/carga/flujo-ciudadano.js`.
- **Cifras.** 30 000 reportes en 60 s repartidos en 12 focos: **p95 de `POST /api/reportes` = 707 ms** (RNF002 pide < 1 s: cumple), **0,05 % de errores**, y todos fueron *timeouts* del pool de
  Mongo (2 s). 772 iteraciones se descartaron: es el límite del generador, no del backend (k6 y 12 contenedores comparten un solo PC). Son cifras de **este PC**, no de un servidor; sirven para
  comparar contra sí mismas, no como promesa.
- **El pool de Mongo falla rápido y no se toca.** Con `max-pool` 100 y espera de 2 s, la presión extrema se convierte en un 503 rápido en vez de una cola que crece sin fin. Subir el pool o la espera
  solo movería el cuello de botella a la base. Los 0,05 % son exactamente eso funcionando.
- **Hallazgo: la carga no cambia estados, y es correcto.** Tras 29 333 reportes anónimos de una sola red, los 211 barrios seguían sin estado. Desde ADR-090 el quórum pide composición (cuentas verificadas o
  más de una red), así que una ráfaga anónima desde una red no mueve el mapa por mucho que sea masiva (D9/D16). La prueba de carga mide la **ruta de escritura y recálculo**; las transiciones se prueban
  con el guion del simulador (ADR-096), no con k6.
- **No se midieron «30 000 suscriptores».** Esa prueba exige la colección `notificaciones` y el tope de avisos (2.8), que están fuera de alcance. No se afirma nada de ella.

**2. Métricas D37: qué mide cada contador y cómo leerlo.** `GET /api/veedor/sistema/metricas` (permiso `VER_PANEL`) devuelve contadores **de este proceso desde `desde`** (un reinicio los pone a cero; con
varias réplicas cada una cuenta los suyos). Para calibrar:

| Contador | Qué cuenta | Cómo leerlo |
|---|---|---|
| `cambiosDeEstado` | cada cambio de estado publicado, por `ESTADO/ORIGEN` | cuántos mueve cada fuente; un `SIN_DATOS/SIN_ORIGEN` es el regreso a «sin datos» |
| `quorumsRechazadosPorComposicion` | episodios en que los votos llegaron al umbral pero no a la composición | si es alto frente a los cambios, la composición (redes mínimas, cuentas verificadas) es estricta o hay ráfagas de una sola red |
| `reportesPorNivelDeVerificacion` | reportes recibidos por `NINGUNA`/`UBICACION_VERIFICADA`/`CUENTA_VERIFICADA` | si casi todo es `NINGUNA`, el quórum rara vez podrá cumplir la composición |
| `tiempoHastaElCambioDeEstado` | del primer reporte que sostiene un estado a su publicación (promedio y máximo) | un máximo cercano a la ventana de consenso sugiere un umbral alto para los barrios pequeños |
| `disputasAbiertas` | barrios que entraron en disputa con Acuacar | vecinos contradiciendo un aviso oficial |
| `fallosDeColectores` | fallos por colector | salud de las fuentes |

**Los umbrales NO se ajustan.** No hay datos reales todavía: el proporcional al barrio, la composición del quórum y la ventana de consenso siguen siendo **valores iniciales configurables**. Las métricas son lo
que permitirá ajustarlos con evidencia, no una razón para hacerlo hoy.

**3. Lo que no se construyó, y por qué.**
- **Reputación por dispositivo: no.** Necesita datos de abuso real para no inventar una fórmula, y el bloqueo de dispositivos (2.6) está fuera de alcance. Es opcional en el plan.
- **D19 (borrar las fotos a los 180 días) sigue DESACTIVADO.** Borrar evidencia es irreversible; se activará cuando alguien lo decida con la retención de los reportes a la vista.
- **Sensores IoT:** construidos, inactivos, fuera de la simulación, de la carga y de la documentación como funcionalidad.

**4. Revisión de dominio de F6: qué se corrigió (cada cambio de comportamiento con una prueba que falló antes; los Javadocs y la regla ArchUnit se comprobaron de otra forma).**
- **El reintento de la transacción nunca se disparaba.** `TransaccionMongoAdapter` buscaba la etiqueta `TransientTransactionError` en la `MongoException`, pero `MongoTemplate` la traduce antes (en la prueba real
  llegó como `DataIntegrityViolationException`) y la etiqueta queda en una causa. Dos recálculos simultáneos del mismo barrio hacían que el perdedor saliera como 503. Ahora se recorre la cadena de causas. Prueba
  unitaria y de integración con **dos transacciones reales** sobre el mismo documento.
- **Un error al evaluar el consenso ya no rompe un reporte guardado** (`RegistrarReporteService`): el ciudadano veía un error, reintentaba y duplicaba. Se registra un aviso y el barrido de puesta al día recalcula.
- **Actualización perdida en los reportes.** Confirmar un reporte podía revivir uno que el veedor acababa de descartar, y aprobar o descartar pisaba confirmaciones recién llegadas, porque se guardaba el
  documento entero. Ahora son escrituras atómicas: `agregarConfirmacionSiVigente` (`$addToSet` con filtro de estado) y `cambiarEstadoDeModeracion` (solo ese campo). Mismo patrón que `asignarFotoSiNoTiene`.
- **Métricas:** `disputaEscrita` se reinicia al inicio de cada intento de la transacción (un reintento contaba una disputa que no escribió); `MetricasDelSistema` conserva el orden de sus mapas
  (`Map.copyOf` lo barajaba); los Javadocs de `RecalcularSectorService` y `RegistrarReporteService` ya no afirman lo que dejó de ser cierto (los boletines de presión baja con ventana sí se representan; Mongo ya
  corre como réplica y admite transacciones).
- **El canal Pub/Sub de SSE se separa por modo.** ADR-096 aceptó que la simulación publicara en el canal de la instancia real («ruido, no fuga»). Pub/Sub de Redis ignora el número de base de datos, así que no
  había forma de aislarlo con `REDIS_DB`. La instancia real conserva `aguavigia:sse:sectores`; cualquier otro modo lleva sufijo (`…:simulacion`). **Reemplaza ese límite de ADR-096.**
- **Índices:** `cortes.estado` (la calidad del dato cuenta los anulados por igualdad) y `propuestas_ingesta` `estadoRevision+finPrometido`.
- **Regla ArchUnit del «único escritor»** (ADR-087): nada en producción llama a `SectorRepository.guardar` ni a `cambiarEstadoSiEs` (solo las pruebas, para sembrar un barrio). Comprobado que la regla muerde: contra las
  clases con pruebas da 14 violaciones.

**Deuda dicha, sin tocar:**
- **El recálculo carga todo el histórico del barrio** (cortes y propuestas aprobadas). Paginarlo cambiaría lo que el resolutor ve, y con el volumen actual no es un problema medido. Se revisa con datos.
- **Las métricas se suman antes del commit si hay una transacción exterior** (`GestionarCorteOficialService`, `RevisarPropuestaIngestaService`): si esa se revierte después, el contador queda de más. Es un
  contador de calibración, no de negocio; arreglarlo bien pide un puerto «después de confirmar». Dicho en el Javadoc de `RecalcularSectorService`.
- **`RevisarPropuestaIngestaService` guarda la propuesta entera:** dos veedores resolviendo la misma propuesta en el mismo instante podrían pisarse. Los dos son de confianza y la propuesta ya valida su estado al
  decidir; el riesgo es una decisión que gana sobre otra, no datos ajenos.
- `CorteAgua.sostieneElEstadoEn` no tiene llamadores en producción (solo pruebas); `SectorRepository.guardar` y `cambiarEstadoSiEs` siguen en el puerto para sembrar pruebas (la regla de arriba los vigila);
  `SimController` deriva el id de un boletín con `hashCode` y parsea fechas (forma, no negocio, y solo existe en la instancia de simulación).

**5. Auditoría de seguridad de F6: ningún bloqueante. Corregido:**
- **Tres scripts borraban datos de la base real si se ejecutaban sin más** (`sembrar-sectores.mjs`, `sembrar-historico-cortes.mjs`, `preparar-pruebas-frontend.mjs`), y el histórico insertaba cortes inventados con
  origen `OFICIAL_ACUACAR`, lo que contradice la regla de ética de datos nº 4. Ahora comparten `scripts/lib/guarda-destructiva.mjs`: se niegan si la URI no es local (`--permitir-remoto`) o si la base ya guarda datos
  que no son de demostración (`--sobre-datos-reales`); son dos permisos distintos. Probado en vivo: contra la base real local (6 reportes, 1 corte) el histórico se negó sin tocar nada. Lo que inserta el histórico
  lleva `datosDeDemostracion: true`. El CI del frontend (base desechable que ya recibió reportes de `sembrar-demo`) pasa `--sobre-datos-reales` a los dos que lo necesitan.
- **`.gitleaks.toml` tenía escrito el valor de un `JWT_SECRET` que estuvo publicado** (ADR-031), o sea, el secreto vivo en el árbol de trabajo. Ya no está en ningún archivo: son cuatro huellas de commit y línea en
  `.gitleaksignore`. Comprobado con gitleaks 8.24.3 (la versión del CI): 470 commits, 0 hallazgos. La escritura de esas huellas depende de los SHA actuales; si se reescribe el historial dejan de hacer falta.
  Un falso positivo de F5 (el token inventado de un correo de prueba) se marca con `gitleaks:allow` en su línea.
- **`POST /api/suscripciones` no limitaba `sectorIds`:** una petición anónima podía provocar cientos de miles de consultas. `@Size(min = 1, max = 211)` (cambia el contrato: `maxItems: 211`) y `distinct()` en el servicio.
- **Menores:** `X-Sim-Key` fuera de las cabeceras CORS (solo la manda el simulador, que no tiene CORS; el frontend no llama a `/api/sim/**`); `permissions: contents: read` en los tres workflows que no lo declaraban;
  una cuenta invitada sin clave ya gasta el tiempo de un BCrypt (el cronómetro delataba qué correos tienen una invitación pendiente); `EscritorCsv` antepone una comilla a los textos que empiezan por
  `=`, `+`, `-` o `@` (inyección de fórmulas en Excel), sin tocar las cifras.

**Deuda de seguridad dicha, sin tocar:** sin límite propio en los endpoints públicos de lectura (`/api/estadisticas`, `/api/cumplimiento`, `/api/bitacora`) más allá del límite por IP; SSE sin tope por IP (sí
global); acciones de GitHub fijadas por etiqueta y no por SHA; Trivy solo analiza `backend`; Dependabot no cubre `/frontend`; `.mcp.json` usa `npx -y @latest`; `@Size` faltantes en las solicitudes del panel
(cuentas autenticadas con permiso); `RestablecerClaveService` limpia el contador de intentos por correo, que no es la llave real del bloqueo (`correo|ip`), así que quien restablece su clave sigue bloqueado hasta
que venza el bloqueo (molestia, no brecha); no hay enfriamiento por cuenta en el restablecimiento; `SembradorAdminInicial` imprime la clave del ADMIN inicial en el log (a propósito, ADR-086).

- **Gana:** una carga medida con método y límites dichos, métricas con una guía de lectura en vez de umbrales inventados, tres escrituras que dejaron de pisarse, y scripts que ya no pueden borrar la base real
  por descuido. **Pierde:** las cifras de carga son de un PC compartido y no valen como capacidad de un servidor; la deuda de arriba sigue ahí hasta que haya datos que la justifiquen.
