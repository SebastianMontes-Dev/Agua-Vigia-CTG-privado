# FE1 · Lo público, completo

**Objetivo:** que la parte pública muestre todo lo que el contrato ya ofrece:
- de dónde sale el estado de un barrio y cuánto lo respalda
- qué tan sólido es el Índice
- el Índice de cada corte
- cuándo los datos son de una simulación
- la pantalla «¿ya volvió el agua?» que abren los correos de aviso

Rama: `feat/fe1-publico-completo`. Depende de FE0. Esfuerzo: 2 sesiones.

## Pantallas y cambios

**Antes de construir, se añaden a [`guia-frontend.md` §5.1/§5.2](../diseno/guia-frontend.md#5-matriz-de-pantallas-y-datos) las filas nuevas**, con sus datos y estados. Son las marcadas con *.

| Dónde | Qué | Contrato |
|---|---|---|
| Ficha `/sectores/:id` | **Insignias del estado**, siempre con texto, nunca solo color:<br>- `origen` («según Acuacar», «según vecinos»…)<br>- `ventanaPrometida` («Acuacar prometió hasta las 18:00», o «(hora ya pasada)» si venció)<br>- `restablecimientoPorConfirmar` («por confirmar»)<br>- `enDisputa` + `reportesEnContra` («en disputa: 7 vecinos dicen otra cosa»)<br>- `respaldo` («11 de 12 vecinos»)<br><br>**El color del estado no cambia por una disputa.** Se conservan las dos fechas: `actualizadoEn` y `verificadoEn` (aviso tras 24 h) | [`sectores-y-tiempo-real.md`](../api/sectores-y-tiempo-real.md#get-apisectores); F1 de [`cambios-para-frontend.md`](../api/cambios-para-frontend.md) |
| Mapa `/` | Una marca discreta para los barrios en disputa o por confirmar, coherente con la ficha. Sin colores nuevos: glifo o trama según `identidad.md` | ídem |
| Ficha: historial de cortes | Por corte: los `cierres[]` por barrio, el cierre `provisional`, `EXPIRADO`, `ANULADO` con su motivo. **Índice del corte** (`GET /api/cumplimiento/cortes/{id}`) solo si está cerrado; `409` = «todavía no se puede medir» | [`bitacora-estadisticas-cumplimiento.md`](../api/bitacora-estadisticas-cumplimiento.md#el-índice-de-cumplimiento) |
| `/cumplimiento` y la ficha | **Calidad del dato junto al número**: `porcentajeProvisional`, `cortesSinCierreConfirmado` y `cortesAnulados`. Si el Índice responde `400`, se pide `GET /api/cumplimiento/calidad` y se dice **con cifras** cuánto falta («12 cortes vencidos sin cierre confirmado»). No hay pantalla vacía | ídem; F4 de `cambios-para-frontend.md` |
| Todas las pantallas | **Banner permanente si `GET /api/sistema/modo` → `SIMULACION`**: «Datos de una simulación; no son reales». Se pide una vez al abrir la aplicación. Con `REAL` no se muestra nada. Si alguna pantalla menciona `cuentasSinteticas`, usa la frase exacta de F4 | F4 de `cambios-para-frontend.md` |
| \* `/sectores/:id/restablecimiento?token=` | «¿Ya volvió el agua a [barrio]?» con un botón «Sí, ya volvió». **El `POST` solo se hace al pulsar**, nunca al cargar, porque los clientes de correo abren los enlaces.<br>- `201` → «Gracias, tu respuesta cuenta junto con la de tus vecinos»<br>- `403 enlace-invalido` → «Este enlace ya no sirve», con un enlace a la ficha<br>- `429` → respetar `Retry-After`<br><br>Quitar el token de la URL y usar `referrer no-referrer` | [`correos-y-enlaces.md` §«¿Ya volvió el agua?»](../api/correos-y-enlaces.md#ya-volvió-el-agua-con-un-toque) |
| Bitácora | Etiqueta de procedencia con los 5 orígenes y el `respaldo` del evento, si lo trae | F1 de `cambios-para-frontend.md` |

Sigue vigente la regla de los cinco segundos: en la ficha, el veredicto («Sin servicio · según Acuacar · prometió hasta las
18:00») va arriba, y las insignias secundarias detrás de «Más detalles» en el celular.

## Pruebas

- Unitarias: el texto de cada combinación de insignias, con `null` en cada campo; el Índice con `400` y su calidad.
- e2e simulado: ficha en disputa, ficha por confirmar, cumplimiento sin datos con cifras, banner de simulación, restablecimiento (éxito, enlace inválido, 429, no vota al cargar).
- e2e real: restablecimiento con un token leído de Mailhog tras un aviso de cambio de estado. Necesita una suscripción confirmada y un barrio que pase a «sin servicio».

## Terminado cuando

- Pasa la puerta completa.
- Ningún campo nuevo del sector se muestra sin texto.
- Con el backend de simulación (`AGUAVIGIA_BACKEND=http://localhost:8082`), el banner aparece en todas las pantallas.

## Prompt para Claude Code

```
Usa la skill disenar-frontend. Lee docs/frontend/README.md, docs/frontend/FE1-publico-completo.md, y las secciones F1 y F4
de docs/api/cambios-para-frontend.md, más los documentos enlazados en la tabla.
En una rama feat/fe1-publico-completo desde main: primero añade a docs/diseno/guia-frontend.md §5 las filas nuevas,
luego construye cada cambio de la tabla. Respeta la regla de los cinco segundos y la identidad visual.
Corre la puerta completa (incluidas capturas en 4 tamaños y 2 temas, y la skill revisar-diseno) y muéstrame el resultado.
Abre el PR cuando esté en verde, no lo fusiones. Si usas subagentes, usa model sonnet.
```
