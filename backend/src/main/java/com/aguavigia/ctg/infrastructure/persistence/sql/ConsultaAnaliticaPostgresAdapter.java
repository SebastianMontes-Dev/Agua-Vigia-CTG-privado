package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.domain.AnaliticaNoDisponibleException;
import com.aguavigia.ctg.domain.ConteosAnaliticos;
import com.aguavigia.ctg.domain.CumplimientoAnalitico;
import com.aguavigia.ctg.domain.PuntoMensualAnalitico;
import com.aguavigia.ctg.domain.RegistroDeCopia;
import com.aguavigia.ctg.domain.ResultadoDeCopia;
import com.aguavigia.ctg.domain.port.out.ConsultaAnaliticaPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Lee las vistas de la copia analítica (ADR-100). Si la base no responde, o todavía no existe el esquema porque no hubo una primera
 * copia, lanza {@link AnaliticaNoDisponibleException}: nunca suple la consulta leyendo de Mongo, porque mezclar fuentes ocultaría el problema.
 */
public class ConsultaAnaliticaPostgresAdapter implements ConsultaAnaliticaPort {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConsultaAnaliticaPostgresAdapter.class);

    private final JdbcTemplate jdbc;

    public ConsultaAnaliticaPostgresAdapter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        // Un PostgreSQL que no contesta no puede dejar colgada la petición del vecino.
        this.jdbc.setQueryTimeout(5);
    }

    @Override
    public CumplimientoAnalitico cumplimientoGlobal() {
        return disponible(() -> jdbc.queryForObject("""
                SELECT pares_cerrados, prometida_s, real_s, desviacion_s, porcentaje_cumplimiento
                  FROM analitica.v_cumplimiento_global
                """, (rs, fila) -> new CumplimientoAnalitico(null, null, rs.getLong("pares_cerrados"),
                rs.getLong("prometida_s"), rs.getLong("real_s"), rs.getLong("desviacion_s"),
                rs.getDouble("porcentaje_cumplimiento"))));
    }

    @Override
    public Optional<CumplimientoAnalitico> cumplimientoDe(String sectorId) {
        return disponible(() -> jdbc.query("""
                SELECT sector_id, nombre, pares_cerrados, prometida_s, real_s, desviacion_s, porcentaje_cumplimiento
                  FROM analitica.v_cumplimiento_por_sector
                 WHERE sector_id = ?
                """, (rs, fila) -> new CumplimientoAnalitico(rs.getString("sector_id"), rs.getString("nombre"),
                rs.getLong("pares_cerrados"), rs.getLong("prometida_s"), rs.getLong("real_s"),
                rs.getLong("desviacion_s"), rs.getDouble("porcentaje_cumplimiento")), sectorId).stream().findFirst());
    }

    @Override
    public List<PuntoMensualAnalitico> cumplimientoMensual() {
        return disponible(() -> jdbc.query("""
                SELECT mes, pares_cerrados, prometida_s, real_s, porcentaje_cumplimiento
                  FROM analitica.v_cumplimiento_mensual
                 ORDER BY mes
                """, (rs, fila) -> new PuntoMensualAnalitico(YearMonth.parse(rs.getString("mes")),
                rs.getLong("pares_cerrados"), rs.getLong("prometida_s"), rs.getLong("real_s"),
                rs.getDouble("porcentaje_cumplimiento"))));
    }

    @Override
    public Optional<Instant> ultimaCopiaOk() {
        return disponible(() -> jdbc.query(
                "SELECT MAX(terminada_en) FROM analitica.copia WHERE resultado = 'OK'",
                (rs, fila) -> instante(rs, 1)).stream().filter(java.util.Objects::nonNull).findFirst());
    }

    @Override
    public List<RegistroDeCopia> ultimasCopias(int limite) {
        return disponible(() -> jdbc.query("""
                SELECT id, iniciada_en, terminada_en, resultado,
                       sectores_en_mongo, cortes_en_mongo, pares_en_mongo,
                       sectores_en_sql, cortes_en_sql, pares_en_sql, detalle
                  FROM analitica.copia
                 ORDER BY id DESC
                 LIMIT ?
                """, (rs, fila) -> new RegistroDeCopia(rs.getLong("id"), instante(rs, 2), instante(rs, 3),
                ResultadoDeCopia.valueOf(rs.getString("resultado")),
                conteos(rs, "sectores_en_mongo", "cortes_en_mongo", "pares_en_mongo"),
                conteos(rs, "sectores_en_sql", "cortes_en_sql", "pares_en_sql"),
                rs.getString("detalle")), limite));
    }

    private static Instant instante(ResultSet rs, int columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    /** Nulo si la copia no llegó a tomar ese conteo (una copia fallida a medias). */
    private static ConteosAnaliticos conteos(ResultSet rs, String sectores, String cortes, String pares) throws SQLException {
        int enSectores = rs.getInt(sectores);
        if (rs.wasNull()) {
            return null;
        }
        return new ConteosAnaliticos(enSectores, rs.getInt(cortes), rs.getInt(pares));
    }

    private <T> T disponible(Supplier<T> consulta) {
        try {
            return consulta.get();
        } catch (DataAccessException fallo) {
            // El detalle técnico va al log y no a la respuesta: es una ruta pública y un mensaje de PostgreSQL revela el esquema.
            log.warn("Consulta analítica fallida: {}", AlmacenAnaliticoPostgresAdapter.causaRaiz(fallo));
            throw new AnaliticaNoDisponibleException(
                    "La copia analítica no está disponible en este momento. Puede que todavía no se haya hecho la primera copia.", fallo);
        }
    }
}
