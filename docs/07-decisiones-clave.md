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
