package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.EventoBitacoraFactory;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.GestionarCorteOficialUseCase;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RF016-RF017 — el veedor registra un corte oficial, lo cierra barrio por barrio, confirma los cierres
 * provisionales y anula lo que se publicó por error. {@code CorteAgua} llega ya construido (el `Builder`
 * del dominio impone sus propias invariantes); este servicio solo aporta la regla que cruza agregados:
 * los sectores afectados tienen que existir.
 *
 * RF026: registrar y cerrar anexan un evento a la bitácora pública por cada barrio, porque
 * `EventoBitacora.sectorId` es singular y un corte puede tocar varios a la vez.
 *
 * Este servicio <b>no mueve el estado de los barrios</b>: guarda el corte y sus eventos y le pide a
 * {@link RecalcularSectorUseCase} —el único escritor— que decida, con el corte ya guardado. Antes cada
 * operación calculaba aquí el estado a mano, y cerrar un corte mientras otro seguía abierto sobre el mismo
 * barrio lo dejaba «con servicio» en falso; eso ahora lo resuelve el resolutor para todas las fuentes.
 *
 * Cada operación es una sola transacción (corte, eventos y estado de todos los barrios): si algo falla a
 * mitad no queda un corte guardado con barrios sin mover. `SectorMongoAdapter` difiere el evento que manda
 * correo y push (y la invalidación de caché) hasta que esa transacción confirme.
 */
public class GestionarCorteOficialService implements GestionarCorteOficialUseCase {

    private final CorteAguaRepository cortes;
    private final SectorRepository sectores;
    private final RegistrarEventoBitacoraUseCase registrarEvento;
    private final RecalcularSectorUseCase recalcular;
    private final RelojPort reloj;
    private final TransaccionPort transaccion;
    private final RegistroDeAuditoria auditoria;

    public GestionarCorteOficialService(CorteAguaRepository cortes,
                                         SectorRepository sectores,
                                         RegistrarEventoBitacoraUseCase registrarEvento,
                                         RecalcularSectorUseCase recalcular,
                                         RelojPort reloj,
                                         TransaccionPort transaccion,
                                         RegistroDeAuditoria auditoria) {
        this.auditoria = auditoria;
        this.cortes = cortes;
        this.sectores = sectores;
        this.registrarEvento = registrarEvento;
        this.recalcular = recalcular;
        this.reloj = reloj;
        this.transaccion = transaccion;
    }

    @Override
    public CorteAgua registrar(CorteAgua corte) {
        // Una sola lectura de todos los sectores (ya cacheada) en vez de un buscarPorId por sector afectado.
        Set<SectorId> existentes = sectores.listarTodos().stream().map(Sector::id).collect(Collectors.toSet());
        for (SectorId sectorId : corte.sectoresAfectados()) {
            if (!existentes.contains(sectorId)) {
                throw new IllegalArgumentException("No existe el sector '" + sectorId.valor() + "'");
            }
        }

        return transaccion.ejecutar(() -> {
            CorteAgua guardado = cortes.guardar(corte);
            guardado.sectoresAfectados().forEach(sectorId ->
                    registrarEvento.registrar(EventoBitacoraFactory.corteAnunciado(guardado, sectorId, reloj.ahora())));
            guardado.sectoresAfectados().forEach(recalcular::recalcular);
            return guardado;
        });
    }

    @Override
    public CorteAgua cerrar(CorteId corteId, Instant horaReal) {
        return transaccion.ejecutar(() -> {
            CorteAgua corte = buscarOLanzar(corteId);
            // Solo los barrios que siguen pendientes se restablecen ahora: los demás ya tienen su evento.
            List<SectorId> pendientes = corte.sectoresAfectados().stream()
                    .filter(sectorId -> corte.cierreDe(sectorId).isEmpty())
                    .toList();
            CorteAgua guardado = cortes.guardar(corte.cerrar(horaReal));
            pendientes.forEach(sectorId ->
                    registrarEvento.registrar(EventoBitacoraFactory.corteRestablecido(guardado, sectorId, reloj.ahora())));
            guardado.sectoresAfectados().forEach(recalcular::recalcular);
            return guardado;
        });
    }

    @Override
    public CorteAgua cerrarSector(CorteId corteId, SectorId sectorId, Instant horaReal) {
        return transaccion.ejecutar(() -> {
            CorteAgua corte = buscarOLanzar(corteId);
            CorteAgua guardado = cortes.guardar(
                    corte.cerrarSector(sectorId, new CierreDeCorte(horaReal, OrigenEstado.VEEDOR, false)));
            registrarEvento.registrar(EventoBitacoraFactory.corteRestablecido(guardado, sectorId, reloj.ahora()));
            recalcular.recalcular(sectorId);
            return guardado;
        });
    }

    @Override
    public CorteAgua confirmarCierre(CorteId corteId, SectorId sectorId, Instant horaReal) {
        return transaccion.ejecutar(() -> {
            CorteAgua corte = buscarOLanzar(corteId);
            CorteAgua guardado = cortes.guardar(
                    corte.confirmarCierre(sectorId, new CierreDeCorte(horaReal, OrigenEstado.VEEDOR, false)));
            recalcular.recalcular(sectorId);
            return guardado;
        });
    }

    @Override
    public CorteAgua anular(CorteId corteId, String motivo, ContextoDeAccion contexto) {
        CorteAgua anulado = transaccion.ejecutar(() -> {
            CorteAgua corte = buscarOLanzar(corteId);
            CorteAgua guardado = cortes.guardar(corte.anular(motivo));
            guardado.sectoresAfectados().forEach(sectorId ->
                    registrarEvento.registrar(EventoBitacoraFactory.corteAnulado(guardado, sectorId, reloj.ahora())));
            guardado.sectoresAfectados().forEach(recalcular::recalcular);
            return guardado;
        });
        // Después de confirmar: un asiento de una anulación que se revirtió sería una mentira en la auditoría.
        auditoria.registrar(AccionAuditada.CORTE_ANULADO, null,
                "Corte '" + corteId.valor() + "': " + motivo, contexto);
        return anulado;
    }

    private CorteAgua buscarOLanzar(CorteId corteId) {
        return cortes.buscarPorId(corteId)
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe el corte '" + corteId.valor() + "'"));
    }
}
