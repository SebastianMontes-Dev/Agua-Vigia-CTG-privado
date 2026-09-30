# AguaVigía CTG — Instrucciones del proyecto

Plataforma web ciudadana de monitoreo del acueducto de **Cartagena de Indias**. Cruza los avisos oficiales de Acuacar con
reportes ciudadanos georreferenciados y publica un **Índice de Cumplimiento** (duración prometida vs. real de cada corte).
Proyecto académico de dos personas que corre **en local**. Resuelve un vacío de información, no un problema hidráulico.

## Reparto y alcance

- **Backend: Sebastian. Frontend: Yordy Pardo (`Jordy-Lv`).** No se crea ni edita nada en `frontend/` salvo que Yordy lo pida.
  Si el backend cambia el contrato (`backend/openapi.yaml`, rutas, correos), se avisa qué debe adaptar el frontend.
- Las ramas remotas activas son `feat/f4-avisos` y `feat/f5-ingreso-panel` (de Yordy). No se borran ni se tocan.

## Stack

Spring Boot 3.5 · Java 21 · Maven (`backend/mvnw`) · MongoDB 7 con réplica de un nodo (documentos + `2dsphere`) · Redis 7
(caché, rate limiting, ventana de consenso, pub/sub) · Mailhog (SMTP de pruebas) · Docker Compose. Sin SDK de IA.
Frontend: React 19 + Vite (contrato: `backend/openapi.yaml`; guía en `docs/api/`).

## Comandos

```bash
cd backend && ./mvnw verify            # compila, pruebas unitarias + ArchUnit; las de integración exigen Docker
docker compose up -d mongo             # servicio por servicio; ver docs/01-levantar-a-mano.md
docker compose up -d --build backend   # levanta también sus dependencias; API en http://localhost:8081
node scripts/generar-referencia-api.mjs  # regenera docs/api/referencia-de-rutas.md desde el contrato
```

## Arquitectura (no negociable)

Arquitectura Limpia (puertos y adaptadores), dependencias siempre hacia adentro:
`domain/` (Java puro) ← `application/` (casos de uso) ← `infrastructure/` (Mongo, Redis, correo, JWT, HTTP saliente) y `api/` (REST, DTO, mappers).

- **Regla de oro:** `domain/` no importa nada de `org.springframework` ni `com.mongodb`. Lo verifica un test ArchUnit y la build falla.
- Controladores sin lógica de negocio; nunca se exponen entidades de dominio (DTO + MapStruct).
- Un caso de uso = una clase = una acción. Objetos de valor como `record` que validan al construir.
- Errores de API en RFC 7807 desde un único `@RestControllerAdvice`. Inyección por constructor. Sin Lombok en `domain/`.
- Identificadores del dominio en español (`CorteAgua`); términos técnicos universales en inglés (`Repository`, `Adapter`).
- Comentarios solo cuando el *porqué* no es obvio. Tests con nombre descriptivo en español (`debeRechazarCorteConFinAnteriorAlInicio()`).

## Ética de datos (no negociable)

1. Se respeta `robots.txt` siempre; no se disfraza el `User-Agent`.
2. No se scrapea Facebook, Instagram ni X.
3. El colector se identifica siempre (`User-Agent` con nombre del proyecto y correo de contacto).
4. Nada llega al mapa público sin verificación: sin la frase exacta del boletín que respalde la extracción, no se publica.
5. Antes de afirmar que una fuente está bloqueada o disponible, se prueba con una petición real (skill `verificar-fuente`).

## Git y autoría

- Todo va **directo a `main`**, sin ramas ni PR. Conventional Commits en español (`tipo(scope): descripción`), un commit por unidad de trabajo.
- Commit y push solo cuando el dueño lo pide. Verificar en local antes de empujar y revisar el CI después.
- **El agente nunca figura como colaborador**: sin `Co-Authored-By` ni firmas de IA en commits o PR (lo refuerzan `includeCoAuthoredBy: false` y `.github/workflows/autoria.yml`).
- Fechas en hora de Cartagena (UTC-5). Sin secretos versionados: van en `.env` (ignorado por git); `.env.example` es la plantilla.

## Documentación

Vive en `docs/`, una guía por tema (índice en `docs/README.md`). `docs/api/` y `docs/diseno/` son referencia para el frontend.
La documentación anterior sigue en el historial: `git show pre-limpieza-docs:<ruta>`. Un dato vive en un solo archivo.
Verificar antes de afirmar: si se dice que un endpoint funciona, se prueba.
