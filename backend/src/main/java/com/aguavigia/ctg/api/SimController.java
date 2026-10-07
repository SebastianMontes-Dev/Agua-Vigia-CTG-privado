package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.BoletinSimuladoRespuesta;
import com.aguavigia.ctg.api.dto.EstadoDelRelojRespuesta;
import com.aguavigia.ctg.api.dto.SesionVeedor;
import com.aguavigia.ctg.api.dto.SolicitudBoletinSimulado;
import com.aguavigia.ctg.api.dto.SolicitudCambioDeReloj;
import com.aguavigia.ctg.domain.BoletinSimulado;
import com.aguavigia.ctg.domain.port.in.ControlarRelojDeSimulacionUseCase;
import com.aguavigia.ctg.domain.port.in.IniciarSesionDeAdminDeSimulacionUseCase;
import com.aguavigia.ctg.domain.port.in.InyectarBoletinSimuladoUseCase;
import com.aguavigia.ctg.domain.port.in.ReiniciarSimulacionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Rutas de la simulación (D26): mover el reloj, hacer llegar boletines y abrir la sesión del ADMIN. Solo existen con
 * {@code aguavigia.sim.habilitada=true}, es decir, en la instancia de simulación; en la real responden 404. Cada una exige
 * {@code X-Sim-Key} ({@link GuardiaDeSimulacion}). Solo traducen HTTP: qué hace cada acción lo deciden los casos de uso.
 */
@Tag(name = "Simulación", description = "Solo en la instancia de simulación (perfil `simulacion`), con X-Sim-Key")
@RestController
@RequestMapping("/api/sim")
@ConditionalOnProperty(prefix = "aguavigia.sim", name = "habilitada", havingValue = "true")
public class SimController {

    private static final ZoneId CARTAGENA = ZoneId.of("America/Bogota");
    private static final String CABECERA = "X-Sim-Key";

    private final GuardiaDeSimulacion guardia;
    private final ControlarRelojDeSimulacionUseCase reloj;
    private final InyectarBoletinSimuladoUseCase boletines;
    private final IniciarSesionDeAdminDeSimulacionUseCase sesionAdmin;
    private final ReiniciarSimulacionUseCase reinicio;

    public SimController(GuardiaDeSimulacion guardia, ControlarRelojDeSimulacionUseCase reloj,
                         InyectarBoletinSimuladoUseCase boletines, IniciarSesionDeAdminDeSimulacionUseCase sesionAdmin,
                         ReiniciarSimulacionUseCase reinicio) {
        this.reinicio = reinicio;
        this.guardia = guardia;
        this.reloj = reloj;
        this.boletines = boletines;
        this.sesionAdmin = sesionAdmin;
    }

    @Operation(summary = "Hora del reloj de la simulación")
    @GetMapping("/reloj")
    public EstadoDelRelojRespuesta consultarReloj(@RequestHeader(value = CABECERA, required = false) String clave) {
        guardia.exigir(clave);
        return EstadoDelRelojRespuesta.de(reloj.consultar());
    }

    @Operation(summary = "Mover el reloj de la simulación",
            description = "Se manda `instante` (lo fija) o `avanzarSegundos` (lo adelanta), no ambos. Los TTL de Redis siguen en tiempo real.")
    @PostMapping("/reloj")
    public EstadoDelRelojRespuesta moverReloj(@RequestHeader(value = CABECERA, required = false) String clave,
                                              @RequestBody SolicitudCambioDeReloj solicitud) {
        guardia.exigir(clave);
        boolean fija = solicitud.instante() != null;
        boolean avanza = solicitud.avanzarSegundos() != null;
        if (fija == avanza) {
            throw new IllegalArgumentException("Manda exactamente uno: `instante` o `avanzarSegundos`");
        }
        return EstadoDelRelojRespuesta.de(fija
                ? reloj.fijarEn(solicitud.instante())
                : reloj.avanzar(Duration.ofSeconds(solicitud.avanzarSegundos())));
    }

    @Operation(summary = "Hacer llegar un boletín simulado a la ingesta",
            description = "Pasa por la misma limpieza, deduplicación, prefiltro, extractor y compuertas que uno de Acuacar en vivo, "
                    + "y el ciclo de ingesta corre en el acto.")
    @PostMapping("/boletines")
    @ResponseStatus(HttpStatus.CREATED)
    public BoletinSimuladoRespuesta inyectarBoletin(@RequestHeader(value = CABECERA, required = false) String clave,
                                                    @Valid @RequestBody SolicitudBoletinSimulado solicitud) {
        guardia.exigir(clave);
        long id = solicitud.id() != null ? solicitud.id() : ((solicitud.titulo() + solicitud.contenido()).hashCode() & 0xffffffffL);
        return BoletinSimuladoRespuesta.de(boletines.inyectar(new BoletinSimulado(id, leerFecha(solicitud.fecha()),
                solicitud.enlace(), solicitud.titulo(), solicitud.contenido(), solicitud.portada())));
    }

    @Operation(summary = "Reiniciar lo que la simulación guarda en memoria del backend",
            description = "Vacía el buzón de boletines y devuelve el reloj a la hora real. Mongo y Redis los suelta el simulador.")
    @PostMapping("/reinicio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reiniciar(@RequestHeader(value = CABECERA, required = false) String clave) {
        guardia.exigir(clave);
        reinicio.reiniciar();
    }

    @Operation(summary = "Sesión del ADMIN inicial, sin clave ni segundo factor",
            description = "Para que el simulador invite al resto de cuentas del panel. Queda en la auditoría.")
    @PostMapping("/sesion-admin")
    public SesionVeedor sesionDeAdmin(@RequestHeader(value = CABECERA, required = false) String clave,
                                      HttpServletRequest peticion) {
        guardia.exigir(clave);
        return SesionVeedor.de(sesionAdmin.iniciar(ContextoHttp.de(peticion)));
    }

    /** Un instante con zona, o la fecha y hora local de Cartagena sin zona que trae la API de WordPress. */
    private static Instant leerFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(fecha).toInstant();
        } catch (DateTimeParseException sinZona) {
            try {
                return LocalDateTime.parse(fecha).atZone(CARTAGENA).toInstant();
            } catch (DateTimeParseException ilegible) {
                throw new IllegalArgumentException("La fecha '" + fecha + "' no es ISO-8601 (con zona, o local de Cartagena sin zona)");
            }
        }
    }
}
