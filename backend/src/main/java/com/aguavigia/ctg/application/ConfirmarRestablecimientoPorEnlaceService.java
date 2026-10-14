package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EnlaceDeRestablecimiento;
import com.aguavigia.ctg.domain.EnlaceDeRestablecimientoInvalidoException;
import com.aguavigia.ctg.domain.EstadoSuscripcion;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.Suscripcion;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.ConfirmarRestablecimientoPorEnlaceUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.domain.port.out.FirmaDeEnlacesPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SuscripcionRepository;

/**
 * El voto pasa por {@link RegistrarReporteUseCase}, el mismo camino de cualquier reporte: el cupo por barrio, el
 * resolutor y el quórum son los de siempre. Lo único propio es quién vota: la suscripción que recibió el enlace, que es un
 * votante como un dispositivo (con huella distinta de la de cualquier cuenta o dispositivo) y sin nada verificado.
 */
public class ConfirmarRestablecimientoPorEnlaceService implements ConfirmarRestablecimientoPorEnlaceUseCase {

    private final FirmaDeEnlacesPort firma;
    private final SuscripcionRepository suscripciones;
    private final RegistrarReporteUseCase registrarReporte;
    private final RelojPort reloj;

    public ConfirmarRestablecimientoPorEnlaceService(FirmaDeEnlacesPort firma, SuscripcionRepository suscripciones,
                                                     RegistrarReporteUseCase registrarReporte, RelojPort reloj) {
        this.firma = firma;
        this.suscripciones = suscripciones;
        this.registrarReporte = registrarReporte;
        this.reloj = reloj;
    }

    @Override
    public ReporteCiudadano confirmar(SectorId sector, String token, String ip) {
        EnlaceDeRestablecimiento enlace = firma.verificar(token)
                .filter(e -> reloj.ahora().isBefore(e.venceEn()))
                .filter(e -> e.sector().equals(sector))
                .orElseThrow(EnlaceInvalido::nuevo);

        // Quien se dio de baja, o nunca confirmó, o ya no sigue este barrio, no vota con un correo viejo.
        suscripciones.buscarPorId(enlace.suscripcion())
                .filter(s -> s.estado() == EstadoSuscripcion.CONFIRMADA)
                .filter(s -> sigue(s, sector))
                .orElseThrow(EnlaceInvalido::nuevo);

        return registrarReporte.registrar(sector, TipoReporte.SERVICIO_RESTABLECIDO, null, null,
                Reportante.anonimo(HuellaDispositivo.deSuscripcion(enlace.suscripcion())), ip, false);
    }

    private static boolean sigue(Suscripcion suscripcion, SectorId sector) {
        return suscripcion.sectorIds().contains(sector);
    }

    /** Un único mensaje para todas las razones: distinguirlas diría qué suscripciones existen. */
    private static final class EnlaceInvalido {
        static EnlaceDeRestablecimientoInvalidoException nuevo() {
            return new EnlaceDeRestablecimientoInvalidoException(
                    "Este enlace ya no sirve: pudo vencer, o ya no recibes los avisos de este barrio.");
        }
    }
}
