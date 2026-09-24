package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.application.AutenticarUsuarioService;
import com.aguavigia.ctg.application.CambiarClaveService;
import com.aguavigia.ctg.application.ConfirmarSuscripcionService;
import com.aguavigia.ctg.application.EmisorDeTokensDeCuenta;
import com.aguavigia.ctg.application.EvaluarConsensoService;
import com.aguavigia.ctg.application.RegistrarLecturaDePresionService;
import com.aguavigia.ctg.application.RegistrarReporteService;
import com.aguavigia.ctg.application.RegistroDeAuditoria;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.ControlIntentosPort;
import com.aguavigia.ctg.domain.port.out.EmisorDeSesionPort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.ReservaDeEvaluacionPort;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.SegundoFactorPort;
import com.aguavigia.ctg.domain.port.out.SuscripcionRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Registra los casos de uso de {@code application/}, que no llevan anotaciones de Spring: el
 * dominio y la aplicación no saben qué contenedor los hospeda. El escaneo recoge los que solo
 * dependen de otros beans; los seis que reciben configuración (`aguavigia.*`) se declaran aquí a
 * mano y quedan excluidos del escaneo.
 */
@Configuration
@ComponentScan(
        basePackages = "com.aguavigia.ctg.application",
        useDefaultFilters = false,
        includeFilters = @Filter(type = FilterType.REGEX,
                pattern = "com[.]aguavigia[.]ctg[.]application[.]([A-Za-z]+Service|EmisorDeTokensDeCuenta|RegistroDeAuditoria)"),
        excludeFilters = @Filter(type = FilterType.REGEX,
                pattern = "com[.]aguavigia[.]ctg[.]application[.]"
                        + "(AutenticarUsuario|CambiarClave|ConfirmarSuscripcion|EvaluarConsenso"
                        + "|RegistrarLecturaDePresion|RegistrarReporte)Service"))
public class CasosDeUsoConfig {

    @Bean
    public AutenticarUsuarioService autenticarUsuarioService(
            UsuarioRepository usuarios, CifradorClavePort cifrador, SegundoFactorPort segundoFactor,
            ControlIntentosPort intentos, EmisorDeSesionPort emisorDeSesion, RegistroDeAuditoria auditoria,
            @Value("${aguavigia.cuentas.maximo-intentos:5}") int maximoIntentos,
            @Value("${aguavigia.cuentas.ventana-intentos-minutos:15}") long ventanaIntentosMinutos,
            @Value("${aguavigia.cuentas.bloqueo-minutos:15}") long bloqueoMinutos) {
        return new AutenticarUsuarioService(usuarios, cifrador, segundoFactor, intentos, emisorDeSesion,
                auditoria, maximoIntentos, ventanaIntentosMinutos, bloqueoMinutos);
    }

    @Bean
    public CambiarClaveService cambiarClaveService(
            UsuarioRepository usuarios, CifradorClavePort cifrador, RevocacionSesionPort revocacion,
            ControlIntentosPort intentos, NotificacionCuentaPort notificaciones, RegistroDeAuditoria auditoria,
            RelojPort reloj,
            @Value("${aguavigia.cuentas.maximo-intentos:5}") int maximoIntentos,
            @Value("${aguavigia.cuentas.ventana-intentos-minutos:15}") long ventanaIntentosMinutos,
            @Value("${aguavigia.cuentas.bloqueo-minutos:15}") long bloqueoMinutos) {
        return new CambiarClaveService(usuarios, cifrador, revocacion, intentos, notificaciones, auditoria,
                reloj, maximoIntentos, ventanaIntentosMinutos, bloqueoMinutos);
    }

    @Bean
    public ConfirmarSuscripcionService confirmarSuscripcionService(
            SuscripcionRepository suscripciones, RelojPort reloj,
            @Value("${aguavigia.suscripcion.horas-vigencia-token:48}") long horasVigenciaToken) {
        return new ConfirmarSuscripcionService(suscripciones, reloj, horasVigenciaToken);
    }

    @Bean
    public EvaluarConsensoService evaluarConsensoService(
            SectorRepository sectores, ReporteCiudadanoRepository reportes, ContadorReportesPort contadorReportes,
            ReservaDeEvaluacionPort reserva, EstrategiaConsenso estrategia,
            RegistrarEventoBitacoraUseCase registrarEvento, RelojPort reloj, TransaccionPort transaccion,
            @Value("${aguavigia.consenso.ventana-minutos:30}") long ventanaMinutos) {
        return new EvaluarConsensoService(sectores, reportes, contadorReportes, reserva, estrategia,
                registrarEvento, reloj, transaccion, ventanaMinutos);
    }

    @Bean
    public RegistrarLecturaDePresionService registrarLecturaDePresionService(
            SectorRepository sectores, RegistrarReporteUseCase registrarReporte,
            @Value("${aguavigia.iot.umbral-presion-baja-psi:15.0}") double umbralPresionBajaPsi) {
        return new RegistrarLecturaDePresionService(sectores, registrarReporte, umbralPresionBajaPsi);
    }

    @Bean
    public RegistrarReporteService registrarReporteService(
            SectorRepository sectores, ReporteCiudadanoRepository reportes, ContadorReportesPort contadorReportes,
            EvaluarConsensoUseCase evaluarConsenso, RelojPort reloj,
            @Value("${aguavigia.reportes.limite-por-dispositivo:3}") int limitePorDispositivo,
            @Value("${aguavigia.reportes.limite-por-sensor:30}") int limitePorSensor,
            @Value("${aguavigia.reportes.ventana-limite-minutos:30}") long ventanaLimiteMinutos) {
        return new RegistrarReporteService(sectores, reportes, contadorReportes, evaluarConsenso, reloj,
                limitePorDispositivo, limitePorSensor, ventanaLimiteMinutos);
    }
}
