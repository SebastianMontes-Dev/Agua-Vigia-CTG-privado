# Diagrama de Componentes de la Arquitectura

Este documento describe la arquitectura del sistema **Agua-Vigía**, que corre completo en un solo PC (`ADR-057`, `ADR-080`), y cómo interactúan sus componentes. Los contenedores son los de `docker-compose.yml`, que lo levanta todo con un solo `docker compose up` (`ADR-086`).

## Diagrama

```mermaid
graph TD
    %% Definición de Estilos
    classDef client fill:#f9f9f9,stroke:#333,stroke-width:2px;
    classDef frontend fill:#42b883,stroke:#333,stroke-width:2px,color:#fff;
    classDef backend fill:#68a063,stroke:#333,stroke-width:2px,color:#fff;
    classDef database fill:#4db33d,stroke:#333,stroke-width:2px,color:#fff;
    classDef cache fill:#dc382d,stroke:#333,stroke-width:2px,color:#fff;
    classDef tools fill:#f8a326,stroke:#333,stroke-width:2px,color:#fff;
    classDef external fill:#e8e8e8,stroke:#666,stroke-width:1px,stroke-dasharray:4 3;

    %% Actores y Clientes
    Client([Usuarios / Administradores]):::client

    %% Capa de Presentación
    subgraph "Capa de Presentación"
        FE["🖥️ Frontend<br/>(React 19 · Vite, frontend/)"]:::frontend
    end

    %% Capa de Lógica y Servicios
    subgraph "Capa de Aplicación y Datos (Docker)"
        API["⚙️ Backend API<br/>(Spring Boot · Java 21)"]:::backend
        DB[("🗄️ Base de Datos<br/>(MongoDB)")]:::database
        Cache[("⚡ Estado efímero y caché<br/>(Redis)")]:::cache
        SMTP["📧 Servidor de Correo local<br/>(MailHog)"]:::tools
        Seed["🌱 Sembrador<br/>(Node, se ejecuta una vez)"]:::tools
    end

    %% Servicios externos
    subgraph "Fuera del PC (Internet)"
        Acuacar["Acuacar<br/>(API REST de WordPress)"]:::external
        RSS["Prensa por RSS<br/>(Google News, Zona Cero, Caracol Radio, W Radio)"]:::external
        TG["API de Telegram<br/>(apagada sin TELEGRAM_BOT_TOKEN)"]:::external
    end

    %% Relaciones
    Client -->|Navegador HTTP| FE
    FE -->|/api y SSE por el proxy de Vite| API

    API -->|Driver MongoDB (TCP)| DB
    API -->|Lectura/Escritura y pub/sub (TCP)| Cache
    API -->|Envío de emails (SMTP)| SMTP
    API -->|Ingesta cada 10 min (HTTPS)| Acuacar
    API -->|Ingesta cada 10 min (HTTPS)| RSS
    API -->|sendMessage y sondeo getUpdates (HTTPS)| TG

    Seed -->|Barrios, histórico y cuentas de demo (TCP)| DB
    Seed -->|Espera al healthcheck y usa la API| API

    Client -.->|Verificación de correos (Web UI HTTP)| SMTP
```

`mongo-init-replica` (inicia el replica set de Mongo) y `mongo-express` (visor de la base, solo con `--profile demo`) también están en `docker-compose.yml`; se omiten del diagrama porque no participan en el flujo de la aplicación.

## Descripción de los Componentes

| Componente | Qué hace | Dónde |
|---|---|---|
| **Frontend** | SPA React 19 + Vite (`ADR-067`), en construcción por fases (`plan-frontend.md`). Habla con el backend solo por HTTP, a través del proxy de desarrollo de Vite. | `frontend/` |
| **Backend** | API REST Spring Boot 3.5 / Java 21 en Arquitectura Limpia: negocio, seguridad, validación, ingesta programada y notificaciones. | `backend/` |
| **MongoDB** | Persistencia principal: sectores con geometría `2dsphere`, cortes, reportes, bitácora, cuentas, suscripciones, propuestas de ingesta, auditoría. | servicio `mongo` |
| **Redis** | Estado efímero y compartido, **no sesiones**: la sesión es un JWT sin estado. Qué guarda: tabla de abajo. | servicio `redis` |
| **MailHog** | SMTP falso: atrapa los correos del backend y los enseña en su interfaz web. | servicio `mailhog` |
| **Sembrador** | Contenedor de un solo uso: espera a que el backend esté sano y deja la base con los barrios, el histórico de cortes y las cuentas de demostración. Repetir `up` no duplica datos (`ADR-086`). | servicio `sembrador`, `scripts/sembrador.mjs` |
| **Acuacar y prensa** | Fuentes de la ingesta: Acuacar por `wp-json` y cuatro feeds RSS (`application.yml:175-188`). Detalle: `comportamiento-del-sistema.md` § Ingesta. | Internet |
| **API de Telegram** | Alertas por Telegram (`RF041`, `ADR-066`). Armada pero apagada hasta tener `TELEGRAM_BOT_TOKEN`. | Internet |

### Qué guarda Redis

| Uso | Clase |
|---|---|
| Revocación de sesiones (lista de JWT cerrados antes de caducar) | `infrastructure/persistence/redis/RedisRevocacionSesionAdapter.java` |
| Límite de peticiones por IP | `infrastructure/ratelimit/RateLimitingInterceptor.java` |
| Intentos fallidos y bloqueo por cuenta | `infrastructure/persistence/redis/RedisControlIntentosAdapter.java` |
| Ventana deslizante de reportes por sector | `infrastructure/persistence/redis/RedisContadorReportesAdapter.java` |
| Reservas de evaluación de consenso | `infrastructure/persistence/redis/RedisReservaDeEvaluacionAdapter.java` |
| Caché de respuestas (`@Cacheable`) | `infrastructure/config/CacheConfig.java` |
| Deduplicación reciente de la ingesta (7 días) | `infrastructure/ingest/DeduplicadorReciente.java` |
| Bloqueo para que una tarea programada corra una sola vez | `infrastructure/scheduling/EjecucionUnicaRedis.java` |
| Pub/sub del canal en vivo (SSE) | `infrastructure/sse/SseSectoresBroadcaster.java` |
