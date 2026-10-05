package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.application.ActualizarEstadosPorVentanaService;
import com.aguavigia.ctg.application.ActualizarPerfilVecinoService;
import com.aguavigia.ctg.application.AutenticarUsuarioService;
import com.aguavigia.ctg.application.CambiarClaveService;
import com.aguavigia.ctg.application.ConfirmarSuscripcionService;
import com.aguavigia.ctg.application.EmisorDeTokensDeCuenta;
import com.aguavigia.ctg.application.EmitirTokenDeSubidaService;
import com.aguavigia.ctg.application.EvaluarConsensoService;
import com.aguavigia.ctg.application.ExpirarCortesVencidosService;
import com.aguavigia.ctg.application.ConsultarModoDelSistemaService;
import com.aguavigia.ctg.application.ListarReportesPendientesService;
import com.aguavigia.ctg.application.RepoblarContadorDeReportesService;
import com.aguavigia.ctg.application.ImportarVecinosSinteticosService;
import com.aguavigia.ctg.application.LimitesDeReporte;
import com.aguavigia.ctg.application.RecalcularSectorService;
import com.aguavigia.ctg.application.RegistrarLecturaDePresionService;
import com.aguavigia.ctg.application.RegistrarPropuestaIngestaService;
import com.aguavigia.ctg.application.RevisarPropuestaIngestaService;
import com.aguavigia.ctg.application.RegistrarReporteService;
import com.aguavigia.ctg.application.RegistrarVecinoService;
import com.aguavigia.ctg.application.VerificarBarrioVecinoService;
import com.aguavigia.ctg.application.RegistroDeAuditoria;
import com.aguavigia.ctg.domain.CompuertaDePublicacion;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.ReglasDeEstado;
import com.aguavigia.ctg.domain.ResolutorDeEstadoSector;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.domain.port.in.RevisarPropuestaIngestaUseCase;
import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import com.aguavigia.ctg.domain.port.out.AuditoriaRepository;
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
import com.aguavigia.ctg.domain.port.out.SubidaDeFotoRepository;
import com.aguavigia.ctg.domain.port.out.SuscripcionRepository;
import com.aguavigia.ctg.domain.port.out.TiempoConstantePort;
import com.aguavigia.ctg.domain.port.out.TokenDeSubidaPort;
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
 * dependen de otros beans; los que reciben configuración (`aguavigia.*`) se declaran aquí a
 * mano y quedan excluidos del escaneo.
 */
@Configuration
@ComponentScan(
        basePackages = "com.aguavigia.ctg.application",
        useDefaultFilters = false,
        includeFilters = @Filter(type = FilterType.REGEX,
                pattern = "com[.]aguavigia[.]ctg[.]application[.]([A-Za-z]+Service|EmisorDeTokensDeCuenta)"),
        excludeFilters = @Filter(type = FilterType.REGEX,
                pattern = "com[.]aguavigia[.]ctg[.]application[.]"
                        + "(ActualizarEstadosPorVentana|ActualizarPerfilVecino|AutenticarUsuario|CambiarClave|ImportarVecinosSinteticos"
                        + "|ConfirmarSuscripcion|ConsultarModoDelSistema|EmitirTokenDeSubida|EvaluarConsenso|ExpirarCortesVencidos|ListarReportesPendientes|RecalcularSector|RepoblarContadorDeReportes"
                        + "|RegistrarLecturaDePresion|RegistrarPropuestaIngesta|RegistrarReporte|RegistrarVecino"
                        + "|RevisarPropuestaIngesta|VerificarBarrioVecino)Service"))
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

    /**
     * Lo que se hace sobre un vecino se audita con la red aproximada y caduca a los `auditoria-vecinos-dias`
     * (0 lo conserva siempre); lo del panel se conserva completo (D19).
     */
    @Bean
    public RegistroDeAuditoria registroDeAuditoria(
            AuditoriaRepository auditoria, UsuarioRepository usuarios, RelojPort reloj,
            @Value("${aguavigia.retencion.auditoria-vecinos-dias:180}") long diasDeVecinos) {
        return new RegistroDeAuditoria(auditoria, usuarios, reloj, Duration.ofDays(diasDeVecinos));
    }

    /** El token de subida de la foto vive unos minutos (D10): el tiempo de elegir la foto y mandarla, no más. */
    @Bean
    public EmitirTokenDeSubidaService emitirTokenDeSubidaService(
            SubidaDeFotoRepository subidas, TokenDeSubidaPort tokens, RelojPort reloj,
            @Value("${aguavigia.reportes.vigencia-subida-minutos:10}") long vigenciaMinutos) {
        return new EmitirTokenDeSubidaService(subidas, tokens, reloj, Duration.ofMinutes(vigenciaMinutos));
    }

    @Bean
    public RegistrarVecinoService registrarVecinoService(
            UsuarioRepository usuarios, EmisorDeTokensDeCuenta emisorDeTokens,
            NotificacionCuentaPort notificaciones, RegistroDeAuditoria auditoria, RelojPort reloj,
            SectorRepository sectores, TiempoConstantePort tiempoConstante,
            @Value("${aguavigia.privacidad.version}") String versionPrivacidad) {
        return new RegistrarVecinoService(usuarios, emisorDeTokens, notificaciones, auditoria, reloj,
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
            @Value("${aguavigia.ubicacion.precision-maxima-metros:200}") double precisionMaximaMetros) {
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

    /** Qué instancia es: `aguavigia.sistema.modo` (REAL por defecto; la instancia de simulación pone SIMULACION). */
    @Bean
    public ConsultarModoDelSistemaService consultarModoDelSistemaService(
            UsuarioRepository usuarios, RelojPort reloj, @Value("${aguavigia.sistema.modo:REAL}") String modo) {
        return new ConsultarModoDelSistemaService(usuarios, com.aguavigia.ctg.domain.ModoDelSistema.Modo.deTexto(modo), reloj);
    }

    /** Las cuentas sintéticas se insertan de a mil: una a una serían 30 000 viajes a la base. */
    @Bean
    public ImportarVecinosSinteticosService importarVecinosSinteticosService(
            UsuarioRepository usuarios, SectorRepository sectores, RegistroDeAuditoria auditoria, RelojPort reloj,
            @Value("${aguavigia.siembra.tamano-de-lote:1000}") int tamanoDeLote) {
        return new ImportarVecinosSinteticosService(usuarios, sectores, auditoria, reloj, tamanoDeLote);
    }

    /**
     * Las compuertas de publicación de Acuacar (D5). Valores iniciales sin datos reales que los respalden: se ajustan con
     * las métricas (F6), no aquí.
     */
    @Bean
    public CompuertaDePublicacion compuertaDePublicacion(
            @Value("${aguavigia.ingesta.compuertas.confianza-minima:0.85}") double confianzaMinima,
            @Value("${aguavigia.ingesta.compuertas.confianza-minima-restablecimiento:0.75}") double confianzaDeRestablecimiento,
            @Value("${aguavigia.ingesta.compuertas.duracion-maxima-horas:72}") long duracionMaximaHoras,
            @Value("${aguavigia.ingesta.compuertas.margen-de-inicio-dias:7}") long margenDeInicioDias,
            @Value("${aguavigia.ingesta.compuertas.maximo-de-barrios:40}") int maximoDeBarrios) {
        return new CompuertaDePublicacion(confianzaMinima, confianzaDeRestablecimiento,
                Duration.ofHours(duracionMaximaHoras), Duration.ofDays(margenDeInicioDias), maximoDeBarrios);
    }

    @Bean
    public RegistrarPropuestaIngestaService registrarPropuestaIngestaService(
            PropuestaIngestaRepository propuestas, SectorRepository sectores, RevisarPropuestaIngestaUseCase revisar,
            RelojPort reloj, CompuertaDePublicacion compuerta) {
        return new RegistrarPropuestaIngestaService(propuestas, sectores, revisar, reloj, compuerta);
    }

    /** Un corte cuya ventana terminó hace más del plazo de expiración se guarda como historia al aprobarse (D27). */
    @Bean
    public RevisarPropuestaIngestaService revisarPropuestaIngestaService(
            PropuestaIngestaRepository propuestas, SectorRepository sectores, RegistrarEventoBitacoraUseCase registrarEvento,
            CorteAguaRepository cortes, RecalcularSectorUseCase recalcular, RelojPort reloj, TransaccionPort transaccion,
            RegistroDeAuditoria auditoria, ReglasDeEstado reglas) {
        return new RevisarPropuestaIngestaService(propuestas, sectores, registrarEvento, cortes, recalcular, reloj,
                transaccion, auditoria, reglas.expiraTrasFin());
    }

    @Bean
    public ExpirarCortesVencidosService expirarCortesVencidosService(
            CorteAguaRepository cortes, RegistrarEventoBitacoraUseCase registrarEvento,
            RecalcularSectorUseCase recalcular, RelojPort reloj, TransaccionPort transaccion, ReglasDeEstado reglas) {
        return new ExpirarCortesVencidosService(cortes, registrarEvento, recalcular, reloj, transaccion,
                reglas.expiraTrasFin());
    }

    @Bean
    public RepoblarContadorDeReportesService repoblarContadorDeReportesService(
            ReporteCiudadanoRepository reportes, ContadorReportesPort contador, RelojPort reloj,
            @Value("${aguavigia.consenso.ventana-minutos:30}") long ventanaMinutos) {
        return new RepoblarContadorDeReportesService(reportes, contador, reloj, Duration.ofMinutes(ventanaMinutos));
    }

    @Bean
    public ListarReportesPendientesService listarReportesPendientesService(
            ReporteCiudadanoRepository reportes, RelojPort reloj,
            @Value("${aguavigia.moderacion.rafaga-ventana-minutos:30}") long ventanaMinutos,
            @Value("${aguavigia.moderacion.rafaga-minima:5}") int minimoDeReportes) {
        return new ListarReportesPendientesService(reportes, reloj, Duration.ofMinutes(ventanaMinutos), minimoDeReportes);
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
            @Value("${aguavigia.ubicacion.precision-maxima-metros:200}") double precisionMaximaMetros,
            HashDeRedPort hashDeRed) {
        return new RegistrarReporteService(sectores, reportes, contadorReportes, evaluarConsenso, reloj, hashDeRed,
                new LimitesDeReporte(limitePorDispositivo, limitePorSensor, limitePorVecino,
                        Duration.ofMinutes(ventanaLimiteMinutos)),
                precisionMaximaMetros);
    }
}
