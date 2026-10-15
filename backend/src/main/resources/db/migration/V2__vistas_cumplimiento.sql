-- El Índice de Cumplimiento en SQL. La fórmula es la misma que la de CalcularCumplimientoService (ADR-022):
--   * cada par corte-barrio con cierre es una fila (D14);
--   * por fila, la duración prometida es fin_prometido - inicio y la real es cierre_hora - inicio;
--   * se SUMAN duraciones, sin promediar porcentajes;
--   * el porcentaje es min(100, prometida * 100 / real), o 100 si la real es 0;
--   * los cortes ANULADO no entran (se publicaron por error).
--
-- Se suma en milisegundos y se pasa a segundos al final, truncando, igual que Mongo (suma en ms) y que Duration.toSeconds():
-- así el resultado coincide al segundo con el de Java aunque haya fracciones.
CREATE VIEW analitica.v_par_cerrado AS
SELECT cs.corte_id, cs.sector_id, c.inicio, c.fin_prometido, cs.cierre_hora, cs.cierre_provisional,
       (EXTRACT(EPOCH FROM (c.fin_prometido - c.inicio)) * 1000)::BIGINT AS prometida_ms,
       (EXTRACT(EPOCH FROM (cs.cierre_hora  - c.inicio)) * 1000)::BIGINT AS real_ms,
       (EXTRACT(EPOCH FROM (c.fin_prometido - c.inicio)))::BIGINT        AS prometida_s,
       (EXTRACT(EPOCH FROM (cs.cierre_hora  - c.inicio)))::BIGINT        AS real_s
FROM analitica.corte_sector cs
JOIN analitica.corte c ON c.id = cs.corte_id
WHERE c.estado <> 'ANULADO' AND cs.cierre_hora IS NOT NULL;

CREATE VIEW analitica.v_cumplimiento_por_sector AS
SELECT sector_id, nombre, pares_cerrados,
       prometida_ms / 1000 AS prometida_s,
       real_ms / 1000      AS real_s,
       FLOOR((real_ms - prometida_ms) / 1000.0)::BIGINT AS desviacion_s,
       CASE WHEN real_ms / 1000 = 0 THEN 100.0
            ELSE LEAST(100.0, (prometida_ms / 1000) * 100.0 / (real_ms / 1000)) END AS porcentaje_cumplimiento
FROM (
    SELECT s.id AS sector_id, s.nombre, COUNT(p.corte_id) AS pares_cerrados,
           COALESCE(SUM(p.prometida_ms), 0)::BIGINT AS prometida_ms,
           COALESCE(SUM(p.real_ms), 0)::BIGINT      AS real_ms
    FROM analitica.sector s
    LEFT JOIN analitica.v_par_cerrado p ON p.sector_id = s.id
    GROUP BY s.id, s.nombre
) por_sector;

CREATE VIEW analitica.v_cumplimiento_global AS
SELECT pares_cerrados,
       prometida_ms / 1000 AS prometida_s,
       real_ms / 1000      AS real_s,
       FLOOR((real_ms - prometida_ms) / 1000.0)::BIGINT AS desviacion_s,
       CASE WHEN real_ms / 1000 = 0 THEN 100.0
            ELSE LEAST(100.0, (prometida_ms / 1000) * 100.0 / (real_ms / 1000)) END AS porcentaje_cumplimiento
FROM (
    SELECT COUNT(*) AS pares_cerrados,
           COALESCE(SUM(prometida_ms), 0)::BIGINT AS prometida_ms,
           COALESCE(SUM(real_ms), 0)::BIGINT      AS real_ms
    FROM analitica.v_par_cerrado
) agregado;

-- El mes se decide en hora de Cartagena, no en UTC: un restablecimiento a las 02:00Z del 1 de agosto fue el 31 de julio para quien lo vivió.
CREATE VIEW analitica.v_cumplimiento_mensual AS
SELECT mes, pares_cerrados,
       prometida_ms / 1000 AS prometida_s,
       real_ms / 1000      AS real_s,
       CASE WHEN real_ms / 1000 = 0 THEN 100.0
            ELSE LEAST(100.0, (prometida_ms / 1000) * 100.0 / (real_ms / 1000)) END AS porcentaje_cumplimiento
FROM (
    SELECT to_char(cierre_hora AT TIME ZONE 'America/Bogota', 'YYYY-MM') AS mes,
           COUNT(*)                   AS pares_cerrados,
           SUM(prometida_ms)::BIGINT  AS prometida_ms,
           SUM(real_ms)::BIGINT       AS real_ms
    FROM analitica.v_par_cerrado
    GROUP BY 1
) por_mes;
