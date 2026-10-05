package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.LimiteDePeticionesExcedidoException;
import com.aguavigia.ctg.domain.ResultadoDeCupo;
import com.aguavigia.ctg.domain.SesionSinCuentaException;
import com.aguavigia.ctg.domain.UbicacionFueraDelBarrioException;
import com.aguavigia.ctg.domain.UbicacionImprecisaException;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.VerificarBarrioVecinoUseCase;
import com.aguavigia.ctg.domain.port.out.CupoPorCuentaPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;

import java.time.Duration;

/**
 * La ubicación exacta es un dato sensible y esta clase la trata como tal: llega como parámetro, se
 * compara con el polígono del barrio y se descarta. Lo único que sale de aquí es «el barrio quedó
 * verificado el día X»; ni la coordenada ni el barrio donde estaba la persona se guardan ni se
 * escriben en la auditoría.
 *
 * La verificación es una señal blanda: una coordenada se puede falsificar desde el cliente. Sirve
 * para subir el costo de votar desde un barrio que no es el propio, no para probar identidad.
 */
public class VerificarBarrioVecinoService implements VerificarBarrioVecinoUseCase {

    private static final Duration VENTANA_DE_INTENTOS = Duration.ofDays(1);

    private final UsuarioRepository usuarios;
    private final SectorRepository sectores;
    private final CupoPorCuentaPort cupo;
    private final RegistroDeAuditoria auditoria;
    private final RelojPort reloj;
    private final int maximoIntentosPorDia;
    private final double precisionMaximaMetros;

    public VerificarBarrioVecinoService(UsuarioRepository usuarios, SectorRepository sectores,
                                        CupoPorCuentaPort cupo, RegistroDeAuditoria auditoria, RelojPort reloj,
                                        int maximoIntentosPorDia, double precisionMaximaMetros) {
        this.usuarios = usuarios;
        this.sectores = sectores;
        this.cupo = cupo;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.maximoIntentosPorDia = maximoIntentosPorDia;
        this.precisionMaximaMetros = precisionMaximaMetros;
    }

    @Override
    public Usuario verificar(UsuarioId vecino, Coordenada coordenada, double precisionMetros,
                             ContextoDeAccion contexto) {
        Usuario actual = usuarios.buscarPorId(vecino)
                .orElseThrow(() -> new SesionSinCuentaException("La sesión ya no corresponde a una cuenta"));
        if (!actual.esVecino()) {
            throw new IllegalStateException("Esta acción solo existe para la cuenta de un vecino");
        }
        if (Double.isNaN(precisionMetros) || precisionMetros < 0) {
            throw new IllegalArgumentException("La precisión de la ubicación debe ser un número en metros, no negativo");
        }
        if (actual.barrioVerificado()) {
            return actual;
        }
        // Antes de gastar un intento: una ubicación aproximada no se evalúa, así que no cuenta.
        if (precisionMetros > precisionMaximaMetros) {
            throw new UbicacionImprecisaException("La ubicación llegó con una precisión de "
                    + Math.round(precisionMetros) + " m y hace falta " + Math.round(precisionMaximaMetros)
                    + " m o menos. Activa el GPS del dispositivo e inténtalo de nuevo.");
        }

        ResultadoDeCupo resultado = cupo.consumir(
                "verificacion-barrio:" + vecino.valor(), maximoIntentosPorDia, VENTANA_DE_INTENTOS);
        if (!resultado.concedido()) {
            throw new LimiteDePeticionesExcedidoException(
                    "Ya usaste los " + maximoIntentosPorDia + " intentos de verificación de hoy.",
                    resultado.hastaRenovar().toSeconds());
        }

        boolean enSuBarrio = sectores.buscarPorCoordenada(coordenada)
                .map(sector -> sector.id().equals(actual.barrio()))
                .orElse(false);
        if (!enSuBarrio) {
            throw new UbicacionFueraDelBarrioException(
                    "La ubicación no cae dentro del barrio que declaraste (" + actual.barrio().valor() + ").");
        }

        Usuario verificado = usuarios.guardarSiNoCambio(actual.verificarBarrio(reloj.ahora()), actual.actualizadoEn());
        auditoria.registrarConAutor(AccionAuditada.BARRIO_VERIFICADO, verificado, verificado,
                "Barrio verificado con la ubicación del momento; la coordenada no se guarda", contexto);
        return verificado;
    }
}
