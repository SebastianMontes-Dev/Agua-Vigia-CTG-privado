package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.application.ActualizarEstadosPorVentanaService;
import com.aguavigia.ctg.application.ActualizarPerfilVecinoService;
import com.aguavigia.ctg.application.AutenticarUsuarioService;
import com.aguavigia.ctg.application.CambiarClaveService;
import com.aguavigia.ctg.application.ConfirmarSuscripcionService;
import com.aguavigia.ctg.application.EmisorDeTokensDeCuenta;
import com.aguavigia.ctg.application.EvaluarConsensoService;
import com.aguavigia.ctg.application.RecalcularSectorService;
import com.aguavigia.ctg.application.RegistrarLecturaDePresionService;
import com.aguavigia.ctg.application.RegistrarReporteService;
import com.aguavigia.ctg.application.RegistrarVecinoService;
import com.aguavigia.ctg.application.VerificarBarrioVecinoService;
import com.aguavigia.ctg.application.RegistroDeAuditoria;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.ReglasDeEstado;
import com.aguavigia.ctg.domain.ResolutorDeEstadoSector;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.CupoPorCuentaPort;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.ControlIntentosPort;
import com.aguavigia.ctg.domain.port.out.EmisorDeSesionPort;
import com.aguavigia.ctg.domain.port.out.HashDeRedPort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.ReservaDeEvaluacionPort;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.SegundoFactorPort;
import com.aguavigia.ctg.domain.port.out.SuscripcionRepository;
import com.aguavigia.ctg.domain.port.out.TiempoConstantePort;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.time.Duration;

/**
 * Registra los casos de uso de {@code application/}, que no llevan anotaciones de Spring: el
 * dominio y la aplicación no saben qué contenedor los hospeda. El escaneo recoge los que solo
 * dependen de otros beans; los once que reciben configuración (`aguavigia.*`) se declaran aquí a
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
                        + "(ActualizarEstadosPorVentana|ActualizarPerfilVecino|AutenticarUsuario|CambiarClave"
                        + "|ConfirmarSuscripcion|EvaluarConsenso|ExpirarCortesVencidos|RecalcularSector"
                        + "|RegistrarLecturaDePresion|RegistrarReporte|RegistrarVecino|VerificarBarrioVecino)Service"))
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
    public RegistrarVecinoService registrarVecinoService(
            UsuarioRepository usuarios, CifradorClavePort cifrador, EmisorDeTokensDeCuenta emisorDeTokens,
            NotificacionCuentaPort notificaciones, RegistroDeAuditoria auditoria, RelojPort reloj,
            SectorRepository sectores, TiempoConstantePort tiempoConstante,
            @Value("${aguavigia.privacidad.version}") String versionPrivacidad) {
        return new RegistrarVecinoService(usuarios, cifrador, emisorDeTokens, notificaciones, auditoria, reloj,
                sectores, tiempoConstante, versionPrivacidad);
    }

    @Bean
    public ActualizarPerfilVecinoService actualizarPerfilVecinoService(
            UsuarioRepository usuarios, RegistroDeAuditoria auditoria, RelojPort reloj, SectorRepository sectores,
            @Value("${aguavigia.privacidad.version}") String versionPrivacidad) {
        return new ActualizarPerfilVecinoService(usuarios, auditoria, reloj, sectores, versionPrivacidad);
    }

    @Bean
    public VerificarBarrioVecinoService verificarBarrioVecinoService(
            UsuarioRepository usuarios, SectorRepository sectores, CupoPorCuentaPort cupo,
            RegistroDeAuditoria auditoria, RelojPort reloj,
            @Value("${aguavigia.vecino.verificacion-barrio.intentos-por-dia}") int intentosPorDia,
            @Value("${aguavigia.vecino.verificacion-barrio.precision-maxima-metros}") double precisionMaximaMetros) {
        return new VerificarBarrioVecinoService(usuarios, sectores, cupo, auditoria, reloj, intentosPorDia,
                precisionMaximaMetros);
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
            SectorRepository sectores, ContadorReportesPort contadorReportes, ReservaDeEvaluacionPort reserva,
            EstrategiaConsenso estrategia, ReglasDeEstado reglas, RecalcularSectorUseCase recalcular,
            @Value("${aguavigia.consenso.ventana-minutos:30}") long ventanaMinutos) {
        return new EvaluarConsensoService(sectores, contadorReportes, reserva, estrategia, reglas, recalcular,
                ventanaMinutos);
    }

    @Bean
    public ActualizarEstadosPorVentanaService actualizarEstadosPorVentanaService(
            PropuestaIngestaRepository propuestas, CorteAguaRepository cortes, RecalcularSectorUseCase recalcular,
            RelojPort reloj, ReglasDeEstado reglas) {
        return new ActualizarEstadosPorVentanaService(propuestas, cortes, recalcular, reloj, reglas.expiraTrasFin());
    }

    @Bean
    public RecalcularSectorService recalcularSectorService(
            SectorRepository sectores, CorteAguaRepository cortes, PropuestaIngestaRepository propuestas,
            ReporteCiudadanoRepository reportes, EstrategiaConsenso estrategia, ResolutorDeEstadoSector resolutor,
            RegistrarEventoBitacoraUseCase registrarEvento, RelojPort reloj, TransaccionPort transaccion,
            @Value("${aguavigia.consenso.ventana-minutos:30}") long ventanaMinutos,
            @Value("${aguavigia.consenso.redes-minimas:2}") int redesMinimas) {
        return new RecalcularSectorService(sectores, cortes, propuestas, reportes, estrategia, resolutor,
                registrarEvento, reloj, transaccion, Duration.ofMinutes(ventanaMinutos), redesMinimas);
    }

    @Bean
    public RegistrarLecturaDePresionService registrarLecturaDePresionService(
            SectorRepository sectores, RegistrarReporteUseCase registrarReporte,
            @Value("${aguavigia.iot.umbral-presion-baja-psi:15.0}") double umbralPresionBajaPsi,
            @Value("${aguavigia.iot.umbral-presion-normal-psi:20.0}") double umbralPresionNormalPsi) {
        return new RegistrarLecturaDePresionService(sectores, registrarReporte, umbralPresionBajaPsi,
                umbralPresionNormalPsi);
    }

    @Bean
    public RegistrarReporteService registrarReporteService(
            SectorRepository sectores, ReporteCiudadanoRepository reportes, ContadorReportesPort contadorReportes,
            EvaluarConsensoUseCase evaluarConsenso, RelojPort reloj,
            @Value("${aguavigia.reportes.limite-por-dispositivo:3}") int limitePorDispositivo,
            @Value("${aguavigia.reportes.limite-por-sensor:30}") int limitePorSensor,
            @Value("${aguavigia.reportes.ventana-limite-minutos:30}") long ventanaLimiteMinutos,
            @Value("${aguavigia.reportes.limite-por-vecino:5}") int limitePorVecino,
            @Value("${aguavigia.reportes.precision-maxima-metros:200}") double precisionMaximaMetros,
            HashDeRedPort hashDeRed) {
        return new RegistrarReporteService(sectores, reportes, contadorReportes, evaluarConsenso, reloj,
                limitePorDispositivo, limitePorSensor, ventanaLimiteMinutos, hashDeRed, precisionMaximaMetros,
                limitePorVecino);
    }
}
