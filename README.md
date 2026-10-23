# AguaVigía CTG

Plataforma ciudadana de monitoreo del acueducto de Cartagena de Indias. Cruza los avisos oficiales de corte de agua con los reportes georreferenciados de los vecinos y publica un **Índice de Cumplimiento**: qué tanto coincide la duración prometida de cada corte con la real.

![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot 3.5](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![MongoDB 7](https://img.shields.io/badge/MongoDB-7-47A248?logo=mongodb&logoColor=white)
![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Redis 7](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![Docker Compose](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

> Proyecto de aula, independiente: no está afiliado a Aguas de Cartagena S.A. E.S.P. ni a ninguna entidad distrital. Corre en local.

## Qué problema resuelve

Cuando se corta el agua en un barrio, la información suele ser incompleta: el aviso oficial dice una duración y nadie mide si se cumplió. AguaVigía no intenta resolver un problema hidráulico; resuelve ese vacío de información. Recoge los avisos oficiales, permite que los ciudadanos reporten lo que ocurre en su sector y compara ambas fuentes para que cualquiera pueda ver qué tan cumplidos son los cortes.

## Qué hace

- **Avisos oficiales:** un colector automático extrae los cortes anunciados. Nada llega al mapa público sin la frase exacta del boletín que respalde la extracción.
- **Reportes ciudadanos georreferenciados:** los vecinos reportan cortes y se valida el consenso entre reportes antes de darlos por ciertos.
- **Índice de Cumplimiento:** duración prometida frente a la real de cada corte, consultable por sector.
- **Cuentas y roles:** sesión con JWT, roles de usuario y segundo factor (TOTP) para administradores.
- **Límites de uso:** control de peticiones para evitar el spam en los reportes.
- **Copia analítica:** el Índice de Cumplimiento se replica en PostgreSQL para análisis (`/api/analitica/**`). Si esa copia no está disponible, el sistema sigue funcionando.

## Arquitectura

El backend sigue **Arquitectura Limpia** (puertos y adaptadores), con las dependencias siempre hacia adentro:

```
domain/  ←  application/  ←  infrastructure/  (Mongo, Redis, correo, JWT, HTTP saliente)
                          ←  api/             (REST, DTO, mappers)
```

- `domain/` es Java puro: no importa nada de `org.springframework` ni de `com.mongodb`. Una prueba de **ArchUnit** lo verifica y, si se rompe la regla, la compilación falla.
- Los controladores no tienen lógica de negocio y nunca exponen entidades de dominio: se usan DTO y MapStruct.
- Cada caso de uso es una clase con una sola acción. Los errores de la API siguen el formato **RFC 7807** desde un único manejador global.

**Datos.** MongoDB (réplica de un nodo, con índices geoespaciales `2dsphere`) es la base principal y la única fuente de verdad. Redis se usa para caché, límite de peticiones y la ventana de consenso. PostgreSQL 17 guarda una copia analítica derivada, que un job llena desde Mongo cada 5 minutos; Mongo nunca lee de ella.

**Contrato.** La API está descrita en [`backend/openapi.yaml`](backend/openapi.yaml) (87 operaciones). El frontend, hecho en React 19 y Vite, se construye contra ese contrato.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Maven, MapStruct |
| Datos | MongoDB 7, PostgreSQL 17 (Flyway), Redis 7 |
| Calidad | JUnit 5, ArchUnit, pruebas de integración con Docker |
| Infraestructura | Docker Compose, Mailhog (SMTP de pruebas) |
| CI | GitHub Actions: backend, frontend, contenedores y dependencias, simulación, escaneo de secretos (gitleaks) |
| Frontend | React 19, Vite, Playwright |

## Cómo levantarlo

Solo hace falta **Docker Desktop**. No se necesita Java, Node ni un `.env`.

```bash
docker compose up -d --build
```

Levanta MongoDB, Redis, PostgreSQL, Mailhog y el backend, y un servicio de siembra carga los 211 barrios y datos sintéticos de prueba. La API queda en `http://localhost:8081`.

Para compilar y correr las pruebas del backend:

```bash
cd backend
./mvnw verify
```

Las pruebas unitarias y las de arquitectura corren directamente; las de integración exigen Docker.

Existe también una **simulación** que ensaya el sistema con un día de cortes acelerado, en una segunda instancia aislada de la real. Está explicada en [`scripts/simulacion/README.md`](scripts/simulacion/README.md).

## Documentación

Todo está en [`docs/`](docs/README.md), con una guía por tema:

- [Levantar y recorrer el backend paso a paso](docs/01-levantar-a-mano.md)
- [Arquitectura](docs/02-arquitectura.md)
- [Base de datos](docs/03-base-de-datos.md)
- [Seguridad y claves](docs/05-seguridad-y-claves.md)
- [Ética de datos](docs/06-etica-de-datos.md)
- [Decisiones clave (ADR)](docs/07-decisiones-clave.md)
- [Guías de la API para el frontend](docs/api/README.md)

## Ética de datos

El colector respeta `robots.txt`, no disfraza su `User-Agent` y se identifica siempre con el nombre del proyecto y un correo de contacto. No se extraen datos de Facebook, Instagram ni X. Más detalle en [`docs/06-etica-de-datos.md`](docs/06-etica-de-datos.md).

## Equipo y estado

Proyecto académico en equipo.

- **Backend:** Sebastian Montes ([@SebastianMontes-Dev](https://github.com/SebastianMontes-Dev)).
- **Frontend:** Yordy Pardo, siguiendo el plan por fases de [`docs/frontend/`](docs/frontend/README.md).
- El resto de colaboradores aparece en la [lista de contribuidores](https://github.com/SebastianMontes-Dev/aguavigia-ctg-monitoreo-acueducto/graphs/contributors) del repositorio.

**Estado.** El backend está construido y documentado; el frontend está en curso. Los sensores IoT están construidos pero inactivos, y las fases 2 y 3 de la copia analítica en PostgreSQL ([`docs/09-postgresql-fase-1.md`](docs/09-postgresql-fase-1.md)) están sin hacer.

## Licencia

Proyecto de aula. Sin licencia de uso externo definida.
