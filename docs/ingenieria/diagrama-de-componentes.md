# Diagrama de Componentes de la Arquitectura

Este documento describe la arquitectura final del sistema **Agua-Vigía**, ilustrando los diferentes componentes y cómo interactúan entre sí.

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
        FE["🖥️ Frontend (por construir)<br/>guía en docs/api"]:::frontend
        PROXY["🛡️ Proxy nginx<br/>(caché de lectura, límites)"]:::tools
    end

    %% Capa de Lógica y Servicios
    subgraph "Capa de Aplicación y Datos (Docker)"
        API["⚙️ Backend API<br/>(Spring Boot · Java 21)"]:::backend
        DB[("🗄️ Base de Datos<br/>(MongoDB)")]:::database
        Cache[("⚡ Caché y Sesiones<br/>(Redis)")]:::cache
        SMTP["📧 Servidor de Correo local<br/>(MailHog)"]:::tools
    end

    %% Relaciones
    Client -->|Navegador HTTP/HTTPS| FE
    FE -->|Peticiones HTTP/REST| PROXY
    PROXY -->|Lecturas cacheadas y resto de la API| API
    
    API -->|Driver MongoDB (TCP)| DB
    API -->|Lectura/Escritura (TCP)| Cache
    API -->|Envío de emails (SMTP)| SMTP
    
    Client -.->|Verificación de correos (Web UI HTTP)| SMTP
```

## Descripción de los Componentes

* **Frontend**: **no está en el repositorio** (`ADR-048`): lo rehará otra persona a partir de `docs/api/`. Habla con el sistema solo por HTTP hacia el proxy.
* **Proxy (nginx)**: único servicio que publica un puerto en producción. Micro-cachea las lecturas públicas, limita por IP y sirve las fotos (`infra/nginx/`, `ADR-049`).
* **Backend**: API RESTful desarrollada con Spring Boot 3.5 y Java 21, en Arquitectura Limpia. Centraliza la lógica de negocio, maneja la seguridad, valida los datos y coordina las peticiones de entrada y salida con los distintos servicios.
* **MongoDB (Base de Datos)**: Sistema de base de datos NoSQL orientado a documentos utilizado para la persistencia de datos principal (ej. usuarios, reportes, configuraciones).
* **Redis (Caché y Sesiones)**: Almacén de estructura de datos en memoria. Se emplea principalmente para la gestión de sesiones de usuario y el almacenamiento en caché de respuestas frecuentes para reducir la carga de la base de datos y acelerar las respuestas de la API.
* **MailHog**: Herramienta de pruebas de correo electrónico con un servidor SMTP falso integrado. Atrapa los correos salientes que envía el backend y proporciona una interfaz web para inspeccionarlos, ideal para los entornos de desarrollo y pruebas.
