# Simulación de AguaVigía

Un día entero de Cartagena en minutos: Acuacar anuncia cortes, cientos de vecinos reportan, el veedor modera y el sistema decide. El guion
(`guion-completo.yaml`) no solo **muestra** lo que pasa: al final de cada acto **comprueba** que pasó (aserciones contra la API), así que sirve
también como prueba de aceptación y corre en CI (`.github/workflows/simulacion-ci.yml`).

Todo ocurre en una **segunda instancia del backend** (`backend-sim`), separada de la real:

| | Instancia real | Simulación |
|---|---|---|
| Puerto de la API | 8081 | **8082** |
| Base de Mongo | `aguavigia` | `aguavigia_sim` |
| Redis | base 0 | **base 1** |
| Reloj | el del sistema | `RelojSimulado` (se fija por `POST /api/sim/reloj`) |
| Boletines | los lee de Acuacar | los entrega el simulador (`POST /api/sim/boletines`) |
| `/api/sim/**` | 404 | activo, protegido con `X-Sim-Key` |

El mapa real no cambia nunca. El simulador se niega a trabajar si la base no acaba en `_sim` o si Redis es la base 0.

## Uso

```bash
node scripts/simulacion/preparar.mjs                                  # una vez: genera SIMULACION_CLAVE en .env (no se muestra)
docker compose --profile simulacion up -d --build backend-sim         # la instancia de simulación en http://localhost:8082
docker compose --profile simulacion run --rm simulador iniciar        # corre el guion a la velocidad del guion (x60)
```

Para ver el resultado en el frontend, apúntalo a la simulación: `AGUAVIGIA_BACKEND=http://localhost:8082`.

### Velocidades

`--velocidad` son minutos simulados por minuto real. El guion completo dura ~12 min a x60.

| Velocidad | 1 s real equivale a | Duración aproximada del guion |
|---|---|---|
| x1 | 1 s | tiempo real (más de 12 h) |
| x10 | 10 s | ~1 h 10 min |
| **x60** (por defecto) | 1 min | ~12 min |
| x300 | 5 min | ~2,5 min (la que usa el CI) |

El cronómetro se **congela** mientras corre una acción o se comprueban las aserciones de un acto (registrar 300 vecinos por HTTP tarda lo que
tarda), así que a x300 el guion no se adelanta a sí mismo.

### Controles (desde otra terminal, con el guion en marcha)

```bash
docker compose --profile simulacion run --rm simulador pausar | reanudar
docker compose --profile simulacion run --rm simulador velocidad 300
docker compose --profile simulacion run --rm simulador saltar 7       # adelanta el reloj 7 horas
docker compose --profile simulacion run --rm simulador estado         # minuto, hora y acto en curso
docker compose --profile simulacion run --rm simulador reiniciar      # deja la simulación como nueva
```

`iniciar` acepta `--velocidad <x>`, `--guion <archivo>`, `--detener-en-fallo` (para al primer acto con aserciones fallidas) y `--reiniciar`.
Si la base de simulación ya trae datos de otra corrida, `iniciar` se niega y dice que corras `reiniciar` o pases `--reiniciar`.
Termina con código distinto de 0 si falla alguna aserción o alguna acción.

`reiniciar` **no** suelta la base entera: el backend crea los índices y la cuenta ADMIN solo al arrancar, y soltarlos dejaría la instancia rota
hasta reiniciar el contenedor. Borra los documentos de todas las colecciones de `aguavigia_sim` (menos la cuenta ADMIN), vacía Redis db 1,
borra de Mailhog **solo** los correos de la simulación (el Mailhog es el mismo que usa la instancia real), devuelve el reloj al real, vacía el
buzón de boletines y vuelve a sembrar los sectores.

## Qué hace el guion

Los minutos cuentan desde las 08:00 del día siguiente (hora de Cartagena).

| Min | Acto | Se comprueba |
|---|---|---|
| 0 | Estado inicial | modo `SIMULACION`; los 211 sectores «sin datos» |
| 0–10 | 300 vecinos registrados por HTTP, con enlace del correo (Mailhog) y verificación de barrio; 6 desde fuera del barrio | 300 activos, ≥ 200 verificados, 6 rechazados con 422 |
| 20 | Boletín A (corte 10:00–16:00) en Manga, Nelson Mandela y San Fernando | `CORTE_PROGRAMADO` ×3; un aviso por correo a cada suscriptor |
| 60 | Boletín B de baja confianza | no sale al mapa; va a la cola; el veedor lo descarta |
| 120 | Inicia la ventana | `SIN_SERVICIO` ×3, origen ACUACAR |
| 150 | Albornoz: 3 reportes (umbral 3) | `SIN_SERVICIO`, origen VECINOS; una foto con token |
| 150 | Crespo: 6 anónimos desde una sola red | **no cambia**; señal de red en la cola de moderación |
| 170–175 | Bocagrande: 6 reportes y el veedor descarta 2 | `SIN_SERVICIO` y luego de vuelta a «sin datos» (`CONSENSO_REVERTIDO`); solo la foto aprobada es pública |
| 200–205 | Manga: 11 vecinos dicen «ya volvió» en plena ventana | disputa (el color no cambia); el veedor cierra solo Manga a las 11:10 |
| 480 | Llega el fin prometido | Nelson Mandela y San Fernando siguen `SIN_SERVICIO`, «por confirmar» |
| 490–505 | Nelson Mandela: 8 vecinos confirman (quórum reducido) | `CON_SERVICIO`; cierre provisional de fuente VECINOS a las ≈16:10 |
| 540 | El veedor revisa los vencidos | confirma Nelson Mandela (16:05); San Fernando sigue sin cierre |
| 620 | Nelson Mandela: 15 reportes de «sin agua» | el mismo corte se reabre |
| 700–705 | Crespo: anuncio de corte para mañana, aviso «se aplaza» y anulación por el veedor | `CORTE_PROGRAMADO`; el aplazamiento no crea otro corte; al anular, «sin datos» y fuera de las estadísticas |
| salto | +7 h y +65 h | Albornoz sin respaldo reciente y luego «sin datos»; San Fernando `EXPIRADO` con `CORTE_EXPIRADO` |
| final | Índice de Cumplimiento | Manga: prometido 6 h, real ≈ 70 min; calidad del dato con cortes sin cierre confirmado y anulados |

## Qué NO hace (y por qué)

- **No simula sensores.** El soporte de sensores IoT está construido pero inactivo; no forma parte de la simulación ni del guion.
- **No toca datos reales** ni la instancia real. Todo lo que crea lleva el dominio `sim.aguavigia.test` y los boletines, el enlace
  `simulacion.local` y «[SIMULACIÓN]» en el título.
- **No hay «redes distintas».** Desde un solo equipo todos los reportes salen de la misma red; por eso `backend-sim` baja
  `AGUAVIGIA_CONSENSO_REDES_MINIMAS` a 1 (la instancia real exige 2). Es un límite de la simulación, no del sistema.
- **No comprueba el tope de avisos de 15 minutos** ni un registro de notificaciones: esa parte (2.8 del plan) está fuera de alcance. Se comprueba que
  los suscriptores reciben avisos por correo.
- **El aplazamiento no anula solo.** Casar un «se aplaza» con el corte que aplaza exige una heurística de barrios y fecha; el sistema reconoce el aviso y
  el veedor decide (ADR-096).

## Piezas

- `simulador.mjs`: línea de comandos.
- `preparar.mjs`: genera `SIMULACION_CLAVE` en `.env` (nunca la imprime ni la sobrescribe).
- `guion-completo.yaml`: el guion (acciones y aserciones por acto).
- `lib/`: `ejecutor` (el bucle), `linea-de-tiempo`, `cronometro`, `acciones`, `aserciones`, `contexto`, `api`, `mailhog`, `foto`,
  `plantilla-boletin`, `planes`, `control`, `redis`, `config`. Las pruebas están en `scripts/pruebas/simulacion-*.test.mjs` (`npm test` en `scripts/`).

Para escribir otro guion: copia `guion-completo.yaml`; las acciones y aserciones posibles están en `lib/guion.mjs` (`ACCIONES`, `ASERCIONES`) y el
simulador valida el archivo antes de tocar nada.
