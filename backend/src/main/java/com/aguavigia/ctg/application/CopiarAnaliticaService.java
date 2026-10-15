package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ConteosAnaliticos;
import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.FilaCorteAnalitica;
import com.aguavigia.ctg.domain.FilaParAnalitica;
import com.aguavigia.ctg.domain.FilaSectorAnalitica;
import com.aguavigia.ctg.domain.ResultadoDeCopia;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.CopiarAnaliticaUseCase;
import com.aguavigia.ctg.domain.port.out.AlmacenAnaliticoPort;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lee de Mongo con los puertos que ya existen (`listarTodos`, solo lectura) y le entrega la copia al almacén analítico (ADR-100).
 *
 * Los cierres salen de {@link CorteAgua#cierres()}: el dominio ya completa los cortes anteriores a los cierres por barrio, que solo
 * traían `finReal`, con un cierre a esa hora en cada barrio. Es lo mismo que cuenta la agregación de Mongo del Índice.
 *
 * Un par que apunta a un barrio que no existe no se copia (la clave foránea lo rechazaría y la copia entera se caería): se omite y
 * la comparación de conteos lo delata con {@link ResultadoDeCopia#CON_DIFERENCIAS}.
 */
public class CopiarAnaliticaService implements CopiarAnaliticaUseCase {

    private static final Logger log = LoggerFactory.getLogger(CopiarAnaliticaService.class);

    private final SectorRepository sectores;
    private final CorteAguaRepository cortes;
    private final AlmacenAnaliticoPort almacen;
    private final RelojPort reloj;

    public CopiarAnaliticaService(SectorRepository sectores, CorteAguaRepository cortes, AlmacenAnaliticoPort almacen,
                                  RelojPort reloj) {
        this.sectores = sectores;
        this.cortes = cortes;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    @Override
    public ResultadoDeCopia copiar() {
        var inicio = reloj.ahora();
        long copiaId;
        try {
            copiaId = almacen.registrarInicioDeCopia(inicio);
        } catch (RuntimeException sinBase) {
            // Sin PostgreSQL no hay donde anotar nada: el siguiente ciclo lo reintenta.
            log.warn("Copia analítica omitida: la base analítica no responde: {}", sinBase.toString());
            return ResultadoDeCopia.FALLIDA;
        }

        ConteosAnaliticos enMongo = null;
        try {
            List<Sector> deMongo = sectores.listarTodos();
            List<CorteAgua> cortesDeMongo = cortes.listarTodos();
            enMongo = new ConteosAnaliticos(deMongo.size(), cortesDeMongo.size(),
                    cortesDeMongo.stream().mapToInt(corte -> corte.sectoresAfectados().size()).sum());

            Set<SectorId> conocidos = new HashSet<>();
            List<FilaSectorAnalitica> filasDeSectores = new ArrayList<>();
            for (Sector sector : deMongo) {
                conocidos.add(sector.id());
                filasDeSectores.add(new FilaSectorAnalitica(sector.id().valor(), sector.nombre(), sector.poblacion()));
            }
            List<String> huerfanos = new ArrayList<>();
            List<FilaCorteAnalitica> filasDeCortes = new ArrayList<>();
            for (CorteAgua corte : cortesDeMongo) {
                filasDeCortes.add(aFila(corte, conocidos, huerfanos));
            }

            ConteosAnaliticos enSql = almacen.reemplazar(filasDeSectores, filasDeCortes, inicio);

            ResultadoDeCopia resultado = enMongo.equals(enSql) ? ResultadoDeCopia.OK : ResultadoDeCopia.CON_DIFERENCIAS;
            String detalle = resultado == ResultadoDeCopia.OK ? null : detalleDeDiferencias(enMongo, enSql, huerfanos);
            if (resultado != ResultadoDeCopia.OK) {
                log.warn("Copia analítica con diferencias: {}", detalle);
            }
            almacen.cerrarCopia(copiaId, reloj.ahora(), resultado, enMongo, enSql, detalle);
            return resultado;
        } catch (RuntimeException fallo) {
            log.warn("La copia analítica falló y quedó la anterior: {}", fallo.toString());
            cerrarComoFallida(copiaId, enMongo, fallo);
            return ResultadoDeCopia.FALLIDA;
        }
    }

    private static FilaCorteAnalitica aFila(CorteAgua corte, Set<SectorId> conocidos, List<String> huerfanos) {
        List<FilaParAnalitica> pares = new ArrayList<>();
        for (SectorId sectorId : corte.sectoresAfectados()) {
            if (!conocidos.contains(sectorId)) {
                huerfanos.add(corte.id().valor() + " -> " + sectorId.valor());
                continue;
            }
            CierreDeCorte cierre = corte.cierres().get(sectorId);
            pares.add(cierre == null
                    ? new FilaParAnalitica(sectorId.valor(), null, null, false)
                    : new FilaParAnalitica(sectorId.valor(), cierre.hora(), cierre.fuente().name(), cierre.provisional()));
        }
        return new FilaCorteAnalitica(corte.id().valor(), corte.origen().name(), corte.estado().name(), corte.causa(),
                corte.ventana().inicio(), corte.ventana().finPrometido(), pares);
    }

    private static String detalleDeDiferencias(ConteosAnaliticos enMongo, ConteosAnaliticos enSql, List<String> huerfanos) {
        StringBuilder detalle = new StringBuilder("Mongo y PostgreSQL no cuadran:");
        if (enMongo.sectores() != enSql.sectores()) {
            detalle.append(" barrios ").append(enMongo.sectores()).append(" vs ").append(enSql.sectores()).append(';');
        }
        if (enMongo.cortes() != enSql.cortes()) {
            detalle.append(" cortes ").append(enMongo.cortes()).append(" vs ").append(enSql.cortes()).append(';');
        }
        if (enMongo.pares() != enSql.pares()) {
            detalle.append(" pares corte-barrio ").append(enMongo.pares()).append(" vs ").append(enSql.pares()).append(';');
        }
        if (!huerfanos.isEmpty()) {
            detalle.append(" ").append(huerfanos.size()).append(" par(es) apuntan a un barrio que no existe (")
                    .append(String.join(", ", huerfanos.subList(0, Math.min(5, huerfanos.size()))))
                    .append(huerfanos.size() > 5 ? ", …" : "").append(')');
        }
        return detalle.toString();
    }

    private void cerrarComoFallida(long copiaId, ConteosAnaliticos enMongo, RuntimeException fallo) {
        try {
            almacen.cerrarCopia(copiaId, reloj.ahora(), ResultadoDeCopia.FALLIDA, enMongo, null, fallo.toString());
        } catch (RuntimeException sinBase) {
            // La fila queda EN_CURSO; la siguiente copia deja la historia al día.
            log.warn("Tampoco se pudo anotar el fallo de la copia {}: {}", copiaId, sinBase.toString());
        }
    }
}
