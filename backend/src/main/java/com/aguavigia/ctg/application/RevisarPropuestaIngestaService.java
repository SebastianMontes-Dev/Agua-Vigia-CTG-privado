package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoRevision;
import com.aguavigia.ctg.domain.EventoBitacoraFactory;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.in.RevisarPropuestaIngestaUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;

import java.time.Duration;

/**
 * M9 + M5 — el punto donde una propuesta automatizada se convierte (o no) en dato público.
 *
 * Aprobar guarda la propuesta y el corte del boletín y le pide a {@link RecalcularSectorUseCase} que decida
 * qué estado corresponde: lo que publica `SectorActualizadoEvent` (correo, push, SSE) y lo que anexa a la
 * bitácora (RF026) lo decide ese único escritor, con la propuesta ya guardada para que la vea. Descartar no
 * toca nada: la propuesta se archiva, no se borra, porque la cola de revisión debe poder auditarse. Anular
 * deshace una aprobación por error: la bitácora es de solo anexado, así que la corrección es un evento nuevo.
 */
public class RevisarPropuestaIngestaService implements RevisarPropuestaIngestaUseCase {

    private final PropuestaIngestaRepository propuestas;
    private final SectorRepository sectores;
    private final RegistrarEventoBitacoraUseCase registrarEvento;
    private final CorteAguaRepository cortes;
    private final RecalcularSectorUseCase recalcular;
    private final RelojPort reloj;
    private final TransaccionPort transaccion;
    private final RegistroDeAuditoria auditoria;
    private final Duration expiraTrasFin;

    public RevisarPropuestaIngestaService(PropuestaIngestaRepository propuestas,
                                           SectorRepository sectores,
                                           RegistrarEventoBitacoraUseCase registrarEvento,
                                           CorteAguaRepository cortes,
                                           RecalcularSectorUseCase recalcular,
                                           RelojPort reloj,
                                           TransaccionPort transaccion,
                                           RegistroDeAuditoria auditoria,
                                           Duration expiraTrasFin) {
        this.auditoria = auditoria;
        this.expiraTrasFin = expiraTrasFin;
        this.propuestas = propuestas;
        this.sectores = sectores;
        this.registrarEvento = registrarEvento;
        this.cortes = cortes;
        this.recalcular = recalcular;
        this.reloj = reloj;
        this.transaccion = transaccion;
    }

    @Override
    public PropuestaIngesta aprobar(PropuestaId id) {
        PropuestaIngesta propuesta = buscarOLanzar(id);
        // Antes de tocar nada: una propuesta ya descartada o anulada lanza aquí y no cambia nada.
        PropuestaIngesta aprobada = propuesta.aprobar();

        sectores.buscarPorId(propuesta.sectorId())
                .orElseThrow(() -> new IllegalStateException(
                        "El sector '" + propuesta.sectorId().valor() + "' de la propuesta ya no existe"));

        // D27: un corte cuya ventana ya terminó es historia. Se guarda como tal, con la fecha del hecho, sin mover el mapa.
        if (propuesta.esCorteVencido(reloj.ahora(), expiraTrasFin)) {
            // Idempotente: aprobar otra vez no vuelve a anexar el evento (la bitácora es de solo anexado).
            if (propuesta.estadoRevision() == EstadoRevision.APROBADA) {
                return propuesta;
            }
            return transaccion.ejecutar(() -> {
                PropuestaIngesta guardada = propuestas.guardar(aprobada);
                cortes.anexarSectorAlCorte(propuesta.idDelCorte(), propuesta.sectorId(), propuesta.inicioDeclarado(),
                        propuesta.finPrometido(), causaDe(propuesta), OrigenCorte.INGESTA_IA, EstadoCorte.EXPIRADO);
                registrarEvento.registrar(EventoBitacoraFactory.corteHistorico(propuesta));
                return guardada;
            });
        }

        // Propuesta, corte y estado son una sola unidad: si el recálculo falla, la aprobación se revierte entera.
        return transaccion.ejecutar(() -> {
            PropuestaIngesta guardada = propuestas.guardar(aprobada);
            registrarCorteDelBoletin(propuesta);
            recalcular.recalcular(propuesta.sectorId());
            return guardada;
        });
    }

    /**
     * M6/M7 — un boletín con ventana declarada es un corte, y sin corte no hay estadísticas: la bitácora cuenta
     * qué pasó, pero `sectoresMasAfectados` y `cortesPorDiaDeSemana` agregan sobre la colección de cortes.
     *
     * **No se cierra el corte.** El boletín dice cuándo *prometieron* restablecer, no cuándo se restableció de
     * verdad. Rellenarlo con la promesa daría un Índice de Cumplimiento del 100% permanente, que es justo la
     * afirmación que este proyecto existe para poder contrastar. El corte queda abierto hasta que el veedor
     * confirme la hora real, los vecinos la sostengan o un boletín de restablecimiento la declare; mientras
     * tanto `CorteAgua.sostieneElEstadoEn` impide que, vencida su ventana, bloquee el retorno a CON_SERVICIO.
     *
     * Anexa el sector con una escritura atómica (`CorteAguaRepository.anexarSectorAlCorte`), no con
     * leer→modificar→guardar: un boletín nombra muchos barrios y genera una propuesta por sector, y con varios
     * veedores a la vez (`ADR-039`) dos aprobaciones del mismo boletín pueden procesarse casi simultáneamente.
     */
    private void registrarCorteDelBoletin(PropuestaIngesta propuesta) {
        if (propuesta.inicioDeclarado() == null || propuesta.finPrometido() == null) {
            return;
        }
        cortes.anexarSectorAlCorte(propuesta.idDelCorte(), propuesta.sectorId(), propuesta.inicioDeclarado(),
                propuesta.finPrometido(), causaDe(propuesta), OrigenCorte.INGESTA_IA, EstadoCorte.ANUNCIADO);
    }

    private static String causaDe(PropuestaIngesta propuesta) {
        return propuesta.citaTextual() == null || propuesta.citaTextual().isBlank()
                ? "Anuncio de " + propuesta.fuente()
                : propuesta.citaTextual();
    }

    @Override
    public PropuestaIngesta descartar(PropuestaId id) {
        return propuestas.guardar(buscarOLanzar(id).descartar());
    }

    @Override
    public PropuestaIngesta anular(PropuestaId id, String motivo, ContextoDeAccion contexto) {
        PropuestaIngesta propuesta = buscarOLanzar(id);
        PropuestaIngesta anulada = propuesta.anular(motivo);

        PropuestaIngesta resultado = transaccion.ejecutar(() -> {
            PropuestaIngesta guardada = propuestas.guardar(anulada);
            registrarEvento.registrar(EventoBitacoraFactory.boletinAnulado(
                    propuesta.sectorId(), motivo, propuesta.urlOriginal(), reloj.ahora()));
            recalcular.recalcular(propuesta.sectorId());
            return guardada;
        });
        auditoria.registrar(AccionAuditada.PROPUESTA_ANULADA, null,
                "Propuesta '" + id.valor() + "': " + motivo, contexto);
        return resultado;
    }

    private PropuestaIngesta buscarOLanzar(PropuestaId id) {
        return propuestas.buscarPorId(id)
                .orElseThrow(() -> new EntidadNoEncontradaException(
                        "No existe la propuesta '" + id.valor() + "'"));
    }
}
