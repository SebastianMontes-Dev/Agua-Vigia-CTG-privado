package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.LimiteReportesExcedidoException;
import com.aguavigia.ctg.domain.NivelDeVerificacion;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.HashDeRedPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * RF005-RF008 — reportar sin registro, en máximo dos toques.
 *
 * RF006 (limitar reportes por dispositivo) lo hace este servicio, no el rate limiting HTTP
 * genérico: ese interceptor usa IP y no huella de dispositivo (ADR-018, decisión deliberada para
 * un problema distinto — fuerza bruta de login), así que dos dispositivos detrás del mismo NAT se
 * limitarían entre sí y uno con varias IPs no se limitaría nunca.
 *
 * El cupo se reserva de forma atómica en Redis (`intentarReservarCupo`) y no contando en Mongo
 * antes de guardar: entre la consulta y la escritura cabían dos peticiones simultáneas del mismo
 * dispositivo, y ambas pasaban. Mongo en instancia única no ofrece transacciones multi-documento
 * con las que cerrar esa ventana; un INCR sí es atómico. Sigue siendo una llave distinta de la del
 * consenso: ese ZSET alimenta RF009-RF011 y a propósito no deduplica por huella, y mezclar ambos
 * controles haría que cambiar uno rompiera el otro sin avisar.
 *
 * RF009: cada reporte dispara la evaluación de consenso de su sector — "automáticamente" no
 * significa "en un job aparte que alguien tiene que acordarse de programar".
 *
 * D16: además de la huella, cada reporte guarda cuánto respalda que quien lo envía está en el barrio
 * (el nivel de verificación) y un resumen diario de su red. Con ellos el quórum puede exigir que no
 * sea una sola persona con varios aparatos ni solo anónimos sin ninguna prueba.
 */
public class RegistrarReporteService implements RegistrarReporteUseCase {

    /** Las fechas del sistema son las de Cartagena: de ahí sale el día de la sal de la red. */
    private static final ZoneId ZONA_DE_CARTAGENA = ZoneId.of("America/Bogota");

    private final SectorRepository sectores;
    private final ReporteCiudadanoRepository reportes;
    private final ContadorReportesPort contadorReportes;
    private final EvaluarConsensoUseCase evaluarConsenso;
    private final RelojPort reloj;
    private final LimitesDeReporte limites;
    private final HashDeRedPort hashDeRed;
    private final double precisionMaximaMetros;

    public RegistrarReporteService(SectorRepository sectores,
                                    ReporteCiudadanoRepository reportes,
                                    ContadorReportesPort contadorReportes,
                                    EvaluarConsensoUseCase evaluarConsenso,
                                    RelojPort reloj,
                                    HashDeRedPort hashDeRed,
                                    LimitesDeReporte limites,
                                    double precisionMaximaMetros) {
        this.sectores = sectores;
        this.reportes = reportes;
        this.contadorReportes = contadorReportes;
        this.evaluarConsenso = evaluarConsenso;
        this.reloj = reloj;
        this.hashDeRed = hashDeRed;
        this.limites = limites;
        this.precisionMaximaMetros = precisionMaximaMetros;
    }

    @Override
    public ReporteCiudadano registrar(SectorId sectorDeclarado, TipoReporte tipo, Coordenada coordenada,
                                       Double precisionMetros, Reportante reportante, String ip,
                                       boolean esSensor) {
        HuellaDispositivo huella = reportante.huella();
        SectorResuelto sector = resolverSector(sectorDeclarado, coordenada);
        SectorId sectorId = sector.id();

        // Un sensor de M13, un vecino con cuenta y un celular anónimo no son el mismo actor: el sensor se
        // autentica con X-IoT-Key y reporta cada pocos minutos por diseño, así que con el cupo ciudadano se
        // autobloqueaba al cuarto envío y el endpoint le devolvía 429; el vecino tiene cuenta, se le puede
        // suspender y su reporte vale más, así que cabe un poco más de cupo. `esSensor` lo decide el llamador
        // (ver javadoc del puerto) y la cuenta la decide el servidor al identificar al reportante: ninguno sale
        // de lo que el cliente escriba.
        int limite = limites.cupoPara(esSensor, reportante.cuentaId() != null);
        Duration ventanaLimite = limites.ventana();

        // Dos guardas con propiedades distintas, y hacen falta las dos:
        //
        // 1. Mongo es la verdad duradera. Sobrevive a un reinicio de Redis, que si no borraria
        //    todos los cupos de golpe. A proposito no filtra por moderacion (BUG-041): si filtrara
        //    DESCARTADO, moderar a un spammer le reiniciaria el cupo.
        // 2. Redis cierra la ventana de carrera. Entre contar en Mongo y guardar el reporte caben
        //    dos peticiones simultaneas del mismo dispositivo que leen el mismo conteo y pasan las
        //    dos; INCR es atomico y Mongo en instancia unica no da transacciones multi-documento.
        //
        // Mongo va primero para no gastar un cupo de Redis en una peticion que ya iba a rechazarse.
        long yaReportados = reportes.contarRecientesPorSectorYDispositivo(sectorId, ventanaLimite, huella);
        if (yaReportados >= limite || !contadorReportes.intentarReservarCupo(sectorId, huella, limite, ventanaLimite)) {
            throw new LimiteReportesExcedidoException(
                    "Ya reportaste %d veces en '%s' en los últimos %d minutos. Espera antes de volver a reportar."
                            .formatted(Math.max(yaReportados, limite), sectorId.valor(), ventanaLimite.toMinutes()));
        }

        Instant ahora = reloj.ahora();
        // La verificación va después del cupo: no se gasta una consulta geográfica en lo que ya se rechazó.
        NivelDeVerificacion nivel = verificar(reportante, sector, coordenada, precisionMetros);
        String redHash = ip == null ? null : hashDeRed.hashear(ip, LocalDate.ofInstant(ahora, ZONA_DE_CARTAGENA));

        ReporteCiudadano ciudadano = new ReporteCiudadano(
                new ReporteId(UUID.randomUUID().toString()),
                sectorId,
                tipo,
                // D8: la coordenada exacta se usa para verificar y se descarta. Un sensor, en cambio, está fijo
                // en un sitio conocido y su posición es parte de lo que mide.
                esSensor ? coordenada : redondeada(coordenada),
                huella,
                ahora).conIdentidad(nivel, redHash);
        // Lo decide el endpoint por el que entró, igual que el cupo: un cliente no se declara sensor con su huella.
        ReporteCiudadano reporte = esSensor ? ciudadano.comoDeSensor() : ciudadano;

        ReporteCiudadano guardado = reportes.guardar(reporte);
        contadorReportes.registrar(sectorId, huella);
        evaluarConsenso.evaluar(sectorId);
        return guardado;
    }

    /**
     * D16. La cuenta con el barrio verificado vale para ese barrio y no para otro; sin ella, una ubicación con buena
     * precisión que cae en el barrio reportado. Una ubicación imprecisa o sin precisión no prueba nada.
     */
    private NivelDeVerificacion verificar(Reportante reportante, SectorResuelto sector, Coordenada coordenada,
                                          Double precisionMetros) {
        if (sector.id().equals(reportante.barrioVerificado())) {
            return NivelDeVerificacion.CUENTA_VERIFICADA;
        }
        boolean ubicacionUtil = coordenada != null && precisionMetros != null
                && precisionMetros >= 0 && precisionMetros <= precisionMaximaMetros;
        if (!ubicacionUtil) {
            return NivelDeVerificacion.NINGUNA;
        }
        // Si el barrio se infirió de esta misma coordenada ya se consultó: no se repite la consulta geográfica.
        boolean dentro = sector.inferidoDeLaCoordenada()
                || sectores.buscarPorCoordenada(coordenada).map(s -> s.id().equals(sector.id())).orElse(false);
        return dentro ? NivelDeVerificacion.UBICACION_VERIFICADA : NivelDeVerificacion.NINGUNA;
    }

    /** Tres decimales son unos 110 m: sirve para ver por dónde cae un reporte sin guardar la casa de nadie. */
    private static Coordenada redondeada(Coordenada coordenada) {
        if (coordenada == null) {
            return null;
        }
        return new Coordenada(Math.round(coordenada.latitud() * 1000) / 1000.0,
                Math.round(coordenada.longitud() * 1000) / 1000.0);
    }

    private record SectorResuelto(SectorId id, boolean inferidoDeLaCoordenada) {
    }

    /**
     * RF007. Un sector declarado por el cliente manda y solo se comprueba que exista. Sin sector declarado, la
     * coordenada lo decide, y una que no cae en ningún barrio se rechaza: es un punto fuera de Cartagena. Si viajan
     * las dos, la coordenada se contrasta con el polígono solo después, y solo para verificar (ver `verificar`).
     */
    private SectorResuelto resolverSector(SectorId sectorDeclarado, Coordenada coordenada) {
        if (sectorDeclarado != null) {
            sectores.buscarPorId(sectorDeclarado).orElseThrow(
                    () -> new IllegalArgumentException("No existe el sector '" + sectorDeclarado.valor() + "'"));
            return new SectorResuelto(sectorDeclarado, false);
        }
        if (coordenada == null) {
            throw new IllegalArgumentException("Indica el sector del reporte o una coordenada para ubicarlo");
        }
        return sectores.buscarPorCoordenada(coordenada)
                .map(sector -> new SectorResuelto(sector.id(), true))
                .orElseThrow(() -> new IllegalArgumentException(
                        "La coordenada no cae en ningún sector de Cartagena"));
    }
}
