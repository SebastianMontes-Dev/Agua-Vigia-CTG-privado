# Diagrama de Clases del Dominio y Principios SOLID

Este documento detalla el Diagrama de Clases del Dominio de Agua-Vigía y la justificación de cómo se aplican los principios SOLID en el contexto de nuestra Arquitectura Limpia (Clean Architecture).

## 1. Diagrama de Clases (Dominio)

Leído de `backend/src/main/java/com/aguavigia/ctg/domain/` el 2026-09-29. Se omiten los identificadores tipados (`SectorId`, `CorteId`…), las excepciones de dominio y los records auxiliares de consulta (`Pagina`, `FiltroBitacora`, `PuntoSerieCumplimiento`…). Si cambia un campo de una entidad, el diagrama se actualiza en el mismo PR.

```mermaid
classDiagram
  %% Value Objects
  class Coordenada {
    <<ValueObject>>
    +double latitud
    +double longitud
  }
  class VentanaTiempo {
    <<ValueObject>>
    +Instant inicio
    +Instant finPrometido
    +Instant finReal
  }
  class HuellaDispositivo {
    <<ValueObject>>
    +String hash
  }
  class EstadoServicio {
    <<enumeration>>
    CON_SERVICIO
    SIN_SERVICIO
    PRESION_BAJA
    CORTE_PROGRAMADO
  }
  class OrigenCorte {
    <<enumeration>>
    OFICIAL_ACUACAR
    INGESTA_IA
    VEEDOR
  }
  class EstadoCorte {
    <<enumeration>>
    ANUNCIADO
    CONFIRMADO
    RESTABLECIDO
  }
  class TipoReporte {
    <<enumeration>>
    SIN_AGUA
    PRESION_BAJA
    SERVICIO_RESTABLECIDO
  }

  class EstadoModeracion {
    <<enumeration>>
    PENDIENTE
    APROBADO
    DESCARTADO
  }
  class EstadoRevision {
    <<enumeration>>
    PENDIENTE
    APROBADA
    DESCARTADA
  }
  class TipoEvento {
    <<enumeration>>
    CORTE_ANUNCIADO
    CORTE_CONFIRMADO_POR_CIUDADANOS
    CORTE_RESTABLECIDO
    CORTE_DETECTADO_POR_INGESTA
  }
  class EstadoCuenta {
    <<enumeration>>
    PENDIENTE_VERIFICACION
    PENDIENTE_APROBACION
    INVITADA
    ACTIVA
    SUSPENDIDA
    RECHAZADA
  }
  class EstadoSuscripcion {
    <<enumeration>>
    PENDIENTE_CONFIRMACION
    CONFIRMADA
    CANCELADA
  }
  class TipoTokenCuenta {
    <<enumeration>>
    VERIFICACION_CORREO
    INVITACION
    RESTABLECER_CLAVE
  }
  class PermisosEfectivos {
    <<ValueObject>>
    +RolVeedor rol
    +Set~Permiso~ concedidos
    +Set~Permiso~ revocados
  }
  class SegundoFactor {
    <<ValueObject>>
    +SecretoTotp secreto
    +Instant confirmadoEn
  }

  %% Entidades
  class Sector {
    <<Entity>>
    +SectorId id
    +String nombre
    +Integer poblacion
    +EstadoServicio estadoActual
    +Instant estadoActualizadoEn
    +Instant estadoVerificadoEn
  }
  class CorteAgua {
    <<Entity>>
    +CorteId id
    +List~SectorId~ sectoresAfectados
    +VentanaTiempo ventana
    +String causa
    +OrigenCorte origen
    +EstadoCorte estado
    +cerrar(Instant finReal) CorteAgua
  }
  class ReporteCiudadano {
    <<Entity>>
    +ReporteId id
    +SectorId sectorId
    +TipoReporte tipo
    +Coordenada coordenada
    +HuellaDispositivo huella
    +Instant timestamp
    +EstadoModeracion estadoModeracion
    +String fotoUrl
    +Set~String~ huellasConfirmacion
  }
  class EventoBitacora {
    <<Entity>>
    +EventoId id
    +TipoEvento tipo
    +SectorId sectorId
    +CorteId corteId
    +Instant timestamp
    +String descripcion
    +EstadoServicio estado
    +String urlOriginal
    +String imagenUrl
    +List~ReporteId~ reportesSustento
  }
  class PropuestaIngesta {
    <<Entity>>
    +PropuestaId id
    +SectorId sectorId
    +EstadoServicio estadoPropuesto
    +String fuente
    +String urlOriginal
    +String citaTextual
    +double confianza
    +Instant detectadaEn
    +EstadoRevision estadoRevision
    +Instant inicioDeclarado
    +Instant finPrometido
    +String imagenUrl
    +Instant publicadoEn
    +String tituloOriginal
  }
  class Usuario {
    <<Entity>>
    +UsuarioId id
    +CorreoElectronico correo
    +String nombre
    +ClaveHash claveHash
    +EstadoCuenta estado
    +PermisosEfectivos permisos
    +SegundoFactor segundoFactor
    +Instant creadoEn
    +Instant actualizadoEn
    +SectorId barrio
  }
  class TokenCuenta {
    <<Entity>>
    +String hash
    +TipoTokenCuenta tipo
    +UsuarioId usuarioId
    +Instant creadoEn
    +Instant usadoEn
  }
  class EventoAuditoria {
    <<Entity>>
    +AuditoriaId id
    +AccionAuditada accion
    +UsuarioId autorId
    +String autorCorreo
    +UsuarioId sujetoId
    +String sujetoCorreo
    +String detalle
    +String ip
    +Instant ocurrioEn
  }
  class Suscripcion {
    <<Entity>>
    +SuscripcionId id
    +CorreoElectronico correo
    +List~SectorId~ sectorIds
    +EstadoSuscripcion estado
    +String tokenConfirmacion
    +Instant creadaEn
  }
  class SuscripcionTelegram {
    <<Entity>>
    +ChatTelegramId chat
    +List~SectorId~ sectorIds
    +Instant creadaEn
  }

  %% Patrones / Dominio de Servicios
  class EstrategiaConsenso {
    <<interface>>
    +seAlcanzaConsenso(long reportesRecientes, Sector sector) boolean
  }
  class UmbralFijoEstrategiaConsenso {
    +seAlcanzaConsenso(long reportesRecientes, Sector sector) boolean
  }
  class UmbralProporcionalEstrategiaConsenso {
    +seAlcanzaConsenso(long reportesRecientes, Sector sector) boolean
  }

  EstrategiaConsenso <|.. UmbralFijoEstrategiaConsenso
  EstrategiaConsenso <|.. UmbralProporcionalEstrategiaConsenso

  %% Relaciones
  CorteAgua "1" *-- "1" VentanaTiempo : contiene
  CorteAgua "1" o-- "*" Sector : afecta (SectorId)
  ReporteCiudadano "1" *-- "1" Coordenada : incluye
  ReporteCiudadano "1" *-- "1" HuellaDispositivo : generado por
  ReporteCiudadano "1" o-- "1" Sector : reporta en
  ReporteCiudadano --> EstadoModeracion
  Sector "1" *-- "1" EstadoServicio : tiene
  EventoBitacora --> TipoEvento
  EventoBitacora "*" o-- "0..1" Sector : sectorId
  EventoBitacora "*" o-- "0..1" CorteAgua : corteId
  EventoBitacora "1" o-- "*" ReporteCiudadano : reportesSustento
  PropuestaIngesta "*" o-- "1" Sector : sectorId
  PropuestaIngesta --> EstadoRevision
  Usuario "1" *-- "1" PermisosEfectivos : permisos
  Usuario "1" *-- "0..1" SegundoFactor : segundoFactor
  Usuario --> EstadoCuenta
  Usuario "*" o-- "0..1" Sector : barrio
  TokenCuenta "*" o-- "1" Usuario : usuarioId
  TokenCuenta --> TipoTokenCuenta
  EventoAuditoria "*" o-- "0..1" Usuario : autorId y sujetoId
  Suscripcion "*" o-- "*" Sector : sectorIds
  Suscripcion --> EstadoSuscripcion
  SuscripcionTelegram "*" o-- "*" Sector : sectorIds
```

---

## 2. Aplicación de los Principios SOLID en la Arquitectura Limpia

La arquitectura del sistema ha sido diseñada priorizando un alto nivel de cohesión y un bajo nivel de acoplamiento. La separación de responsabilidades a través de los principios SOLID asegura que el core del negocio (el Dominio) se mantenga intacto y libre de dependencias de infraestructura, bases de datos o frameworks (reglas de la Arquitectura Limpia).

### Single Responsibility Principle (SRP)
**Principio de Responsabilidad Única:** Cada clase, módulo o capa debe tener una y solo una razón para cambiar.
*   **En el Dominio:** Las entidades representan conceptos únicos y cohesionados. Por ejemplo, `CorteAgua` maneja exclusivamente el estado y las transiciones del ciclo de vida de un corte (ej., `cerrar(Instant finReal)` es su única transición a restablecido). No sabe cómo guardarse en una base de datos ni cómo ser serializado a JSON.
*   **En los Casos de Uso (`port/in`):** En lugar de servicios "God Class" (ej. `SistemaService`), se diseñan casos de uso atómicos, como `EvaluarConsensoUseCase` o `RegistrarReporteUseCase`. Cada clase atiende un único flujo de negocio.

### Open/Closed Principle (OCP)
**Principio de Abierto/Cerrado:** Las entidades de software (clases, módulos, funciones, etc.) deben estar abiertas a la extensión, pero cerradas a la modificación.
*   **En el Dominio (Patrón Strategy):** La interfaz `EstrategiaConsenso` permite introducir nuevas formas de calcular los consensos (ej. una futura estrategia basada en IA o un modelo mixto) creando una nueva clase que la implemente, sin necesidad de tocar el código de `UmbralFijoEstrategiaConsenso`, `UmbralProporcionalEstrategiaConsenso` o los casos de uso que la invocan.
*   **En la Arquitectura:** Los adaptadores de infraestructura están aislados de la lógica del núcleo. Si el día de mañana se cambia de MongoDB a PostgreSQL, el código de dominio no necesita ninguna modificación.

### Liskov Substitution Principle (LSP)
**Principio de Sustitución de Liskov:** Las clases derivadas deben poder sustituir a sus clases base sin alterar el correcto funcionamiento del programa.
*   **En el Dominio:** Cualquier implementación de la interfaz `EstrategiaConsenso` puede ser inyectada en el `EvaluarConsensoUseCase` y funcionar perfectamente. El contrato es `boolean seAlcanzaConsenso(long reportesRecientes, Sector sector)` (`domain/EstrategiaConsenso.java:10`); quien arma el `ResultadoConsenso` es el caso de uso, no la estrategia.

### Interface Segregation Principle (ISP)
**Principio de Segregación de Interfaces:** Los clientes no deben verse obligados a depender de interfaces que no utilizan.
*   **En los Puertos de Salida (`port/out`):** En lugar de tener un gigantesco `DatabaseRepository` que declare todos los métodos (CRUD de sectores, reportes, eventos y cortes), se han definido interfaces atómicas y específicas como `SectorRepository`, `CorteAguaRepository`, y `ContadorReportesPort`. Los casos de uso inyectan únicamente los repositorios que realmente necesitan.
*   **Gestión del Tiempo:** El `RelojPort` expone únicamente `Instant ahora()` (`domain/port/out/RelojPort.java:8`). Esto impide que la capa de dominio dependa de utilidades sistémicas pesadas, y facilita el uso de *mocks* precisos para pruebas (ej. validar invariantes en la clase `VentanaTiempo`).

### Dependency Inversion Principle (DIP)
**Principio de Inversión de Dependencias:** Los módulos de alto nivel (Dominio) no deben depender de los módulos de bajo nivel (Infraestructura). Ambos deben depender de abstracciones (interfaces).
*   **En la Arquitectura Limpia:** La capa de `domain` y `application` no importa anotaciones de frameworks (como `@Document` de MongoDB, `@Entity` de JPA o `@Service` de Spring). Los servicios de aplicación dependen de abstracciones (interfaces de puertos, ej., `ContadorReportesPort`), y es la capa externa de infraestructura (ej., `RedisContadorReportesAdapter`) la que depende de esas abstracciones para proporcionar la implementación concreta y funcional. Esto invierte la tradicional dependencia de "Capa de Negocio → Capa de Datos".
