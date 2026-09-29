# Diagrama de Componentes de la Arquitectura

Este documento describe la arquitectura del sistema **Agua-Vigía**, que corre completo en un solo PC (`ADR-057`, `ADR-080`), y cómo interactúan sus componentes.

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
        Cache[("⚡ Caché y Sesiones<br/>(Redis)")]:::cache
        SMTP["📧 Servidor de Correo local<br/>(MailHog)"]:::tools
    end

    %% Relaciones
    Client -->|Navegador HTTP| FE
    FE -->|/api y SSE por el proxy de Vite| API
    
    API -->|Driver MongoDB (TCP)| DB
    API -->|Lectura/Escritura (TCP)| Cache
    API -->|Envío de emails (SMTP)| SMTP
    
    Client -.->|Verificación de correos (Web UI HTTP)| SMTP
```

## Descripción de los Componentes

* **Frontend**: SPA en `frontend/` (React 19 + Vite, `ADR-067`), en construcción por fases (`docs/ingenieria/plan-frontend.md`). Habla con el backend solo por HTTP, a través del proxy de desarrollo de Vite.
* **Backend**: API RESTful desarrollada con Spring Boot 3.5 y Java 21, en Arquitectura Limpia. Centraliza la lógica de negocio, maneja la seguridad, valida los datos y coordina las peticiones de entrada y salida con los distintos servicios.
* **MongoDB (Base de Datos)**: Sistema de base de datos NoSQL orientado a documentos utilizado para la persistencia de datos principal (ej. usuarios, reportes, configuraciones).
* **Redis (Caché y Sesiones)**: Almacén de estructura de datos en memoria. Se emplea principalmente para la gestión de sesiones de usuario y el almacenamiento en caché de respuestas frecuentes para reducir la carga de la base de datos y acelerar las respuestas de la API.
* **MailHog**: Herramienta de pruebas de correo electrónico con un servidor SMTP falso integrado. Atrapa los correos salientes que envía el backend y proporciona una interfaz web para inspeccionarlos, ideal para los entornos de desarrollo y pruebas.
