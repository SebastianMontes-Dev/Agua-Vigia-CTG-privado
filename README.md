# AguaVigía CTG

**Plataforma ciudadana de monitoreo y trazabilidad del servicio de acueducto en Cartagena de Indias.**

AguaVigía cruza los avisos oficiales con reportes ciudadanos georreferenciados para publicar un **Índice de Cumplimiento** que compara la duración prometida de cada corte con la real.

> Cartagena de Indias · 2026

**Estado actual:** backend, bases de datos e infraestructura completos. RF041 (alertas por Telegram) está construido y
armado, y se activa con `TELEGRAM_BOT_TOKEN`; falta conectarlo al bot real. Pruebas de backend en verde (cifra y fecha en
[`estado-del-backend.md`](docs/ingenieria/estado-del-backend.md) §2), con la
cobertura de `domain/` y `application/` por encima del 85% que exige la build (`./mvnw verify` con Docker
abierto). El detalle requisito por requisito, con el nombre de la prueba que sostiene cada uno, está en la
[matriz de trazabilidad](docs/ingenieria/matriz-trazabilidad.md).

**`main` ya no incluye frontend** (`ADR-048`; su código sigue en la etiqueta git `pre-retiro-frontend`): se rehace en
otras ramas de este mismo repositorio y luego se junta todo. Es un **proyecto académico que corre en local** (`ADR-057`). La guía completa de funcionalidades, rutas y reglas para
hacerlo está en [`docs/api/`](docs/api/README.md), y el contrato exacto en
[`backend/openapi.yaml`](backend/openapi.yaml). El requisito de **50 000 usuarios simultáneos** y su estado
real están en [`docs/ingenieria/escalabilidad.md`](docs/ingenieria/escalabilidad.md).

📄 **¿Retomas el backend o entras nuevo al proyecto?** Empieza por
[estado-del-backend.md](docs/ingenieria/estado-del-backend.md): qué está hecho, qué falta, qué se
dejó fuera de alcance y las trampas del entorno.

---

## 🏗️ Arquitectura y Stack Tecnológico

El proyecto está construido bajo una estricta **Arquitectura Limpia (Puertos y Adaptadores)**, garantizando que la lógica de dominio (Java puro) esté totalmente aislada de la infraestructura y el framework web. El cumplimiento de las capas arquitectónicas se evalúa automáticamente mediante **ArchUnit**.

- **Backend:** Spring Boot 3.5 · Java 21 · Maven
- **Base de Datos Principal:** MongoDB (Consultas Geoespaciales `2dsphere`)
- **Caché y Seguridad:** Redis (Rate Limiting y Deduplicación)
- **Pruebas de Integración:** Testcontainers (Bases de datos efímeras reales)
- **Entorno:** Docker Compose, solo local (sin despliegue, `ADR-057` y `ADR-080`) · GitHub Actions (pruebas + ArchUnit, construcción de la imagen, escaneo de secretos y de vulnerabilidades)

---

## 🧩 Módulos del Sistema

| # | Módulo | Descripción |
|---|---|---|
| **M1** | Mapa en vivo | Renderización Geoespacial de los sectores afectados. |
| **M2** | Reporte ciudadano | Formulario de 2 toques sin registro con rate limiting. |
| **M3** | Consenso automático | Cambio de estado de un barrio basado en masa crítica de reportes. |
| **M4** | Alertas por correo | Notificaciones (Doble Opt-In) al cambiar el estado de un barrio. |
| **M5** | Panel del veedor | Backoffice JWT para moderación y registro de cortes. |
| **M6** | Índice de Cumplimiento | Diferencial de tiempo prometido vs real de reparación. |
| **M7** | Estadísticas | Sectores más afectados, cortes por día, duración promedio, evolución del índice mes a mes y exportación en CSV. |
| **M8** | Bitácora pública | Registro cronológico inmutable de todo lo acontecido. |
| **M9** | Ingesta automatizada | Colectores de la API oficial y RSS de prensa, con deduplicación por hash, reintentos y cortacircuitos. La clasificación por IA (RF032-036) se descartó por bloqueo de dependencias (`ADR-025`); queda una heurística por expresiones regulares que **propone** cambios de estado a una cola de revisión del veedor, sin publicar nada por su cuenta (`ADR-028`). |
| **M10** | Evidencia Multimedia | Soporte de capturas fotográficas en reportes. |
| **M11** | Validación Comunitaria | Confirmaciones de un toque para un reporte existente. |
| **M12** | API Abierta Open311 | Estándar internacional para consumo de datos cívicos. |
| **M13** | Integración IoT Pasiva | Telemetría en tiempo real desde sensores de presión locales. |
| **M14** | Alertas Push | Alertas por Telegram (RF041, `ADR-066`): el bot recibe por sondeo y avisa al cambiar el estado de un sector. Apagado hasta poner `TELEGRAM_BOT_TOKEN`; sin probar contra Telegram real. Guía: `docs/ingenieria/telegram.md`. |
| **M15** | Cuentas y permisos | Cuentas individuales del panel (RF042-RF046). Registro abierto con verificación de correo y aprobación de un administrador, o invitación directa con rol asignado. Roles (ADMIN/VEEDOR/OBSERVADOR) como paquetes de permisos, con ajustes por persona; segundo factor TOTP obligatorio para ADMIN; revocación inmediata de sesiones y bitácora de auditoría. Reemplaza la credencial compartida de `ADR-016` — ver `ADR-039`. |

---

## 🚀 Cómo levantar el proyecto

Solo hace falta **Docker** en marcha (Docker Desktop en Windows o macOS) y, para clonar, **Git con Git LFS** (el mapa base
del frontend se versiona con LFS). No hace falta `.env`, Java ni Node en el equipo (`ADR-086`).

```bash
docker compose up
```

Eso construye las imágenes, levanta MongoDB (replica set de un nodo), Redis, MailHog y el backend, y el servicio
`sembrador` deja la base lista: los 211 barrios de Cartagena, **30 000 cuentas de demostración**, el histórico de cortes de
mayo a julio y algunos barrios afectados por consenso real. Termina con `Datos listos: … usuarios …`. Repetir `up` no duplica
nada. La primera vez necesita internet para descargar imágenes y dependencias; después funciona sin conexión.

| Qué | Dónde |
|---|---|
| API y Swagger UI | `http://localhost:8081/swagger-ui.html` |
| Correos que envía el sistema (MailHog) | `http://localhost:8025` |
| Visor web de Mongo (opcional: `docker compose --profile demo up`) | `http://localhost:8082` |
| MongoDB para Compass o `mongosh` | `mongodb://localhost:27017/?directConnection=true` (base `aguavigia`) |

**Entrar al panel del veedor.** Al arrancar con la base vacía, el backend crea el primer administrador
(`admin@aguavigia.local`) con una clave aleatoria que escribe **una sola vez** en su log:

```bash
docker compose logs backend | findstr "ADMINISTRADOR"      # Windows (PowerShell o cmd)
docker compose logs backend | grep ADMINISTRADOR           # Linux, macOS o Git Bash
```

El rol `ADMIN` exige segundo factor: su primera sesión solo sirve para darlo de alta, y el código de 6 dígitos se calcula sin
app con `docker compose run --rm sembrador totp <SECRETO>`. Las cuentas de demostración `VEEDOR` y `OBSERVADOR` activas entran
con la clave pública `DemoAguaVigia-2026` (detalle en
[`credenciales-y-accesos.md`](docs/ingenieria/credenciales-y-accesos.md)).

**Datos en vivo, sin instalar nada:**

```bash
docker compose run --rm sembrador verificar                                # conteo por colección y mínimos de la entrega
docker compose run --rm sembrador agregar-usuarios --cantidad 1000         # 1 000 usuarios nuevos (faker), distintos cada vez
```

**Después de traer cambios del repositorio**, reconstruye: `docker compose up --build`. Para apagar: `docker compose down`
conserva los datos; `docker compose down -v` los borra y el siguiente `up` vuelve a sembrar. Para cambiar un valor por defecto
(puertos, secreto fijo, correo del admin), copia `.env.example` a `.env`: todo lo demás está en
[`entorno-local.md`](docs/ingenieria/entorno-local.md).

---

## 🔀 CORS y desarrollo del frontend

El backend **no emite cabeceras CORS por defecto** (`aguavigia.cors.origenes-permitidos` vacío). Los perfiles
locales `dev` y `docker` sí los abren para los dev servers habituales (5173, 3000 y 4200; `CORS_ORIGENES` los
reemplaza). El frontend de `frontend/` además pasa por el proxy de Vite, así que en desarrollo no hace peticiones
cruzadas. Detalle en
[`docs/api/errores-y-limites.md`](docs/api/errores-y-limites.md#cors).

---

## 🧪 Pruebas y Aseguramiento de Calidad (QA)

El backend de AguaVigía tiene pruebas unitarias y de integración (cifra al día en
[`estado-del-backend.md`](docs/ingenieria/estado-del-backend.md) §2), y la build falla si la
cobertura de `domain/` o `application/` baja del 85% (RNF017) o si se viola una capa de la
arquitectura (RNF018, ArchUnit).

**30 clases de prueba exigen Docker** (Testcontainers levanta MongoDB y Redis reales). Sin un motor de
Docker en marcha fallan con `Could not find a valid Docker environment`; no son defectos del código.

Para correr la suite de pruebas localmente, asegúrate de tener Docker abierto y ejecuta:

```bash
cd backend
./mvnw clean test
```

Este comando descargará las imágenes temporales de MongoDB y Redis, levantará el ecosistema en entornos efímeros, correrá la suite y se destruirá a sí mismo garantizando cero residuos locales.

---
*Plataforma ciudadana e independiente. No está afiliada a Aguas de Cartagena S.A. E.S.P. ni a ninguna entidad distrital.*
