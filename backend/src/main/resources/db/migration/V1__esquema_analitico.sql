-- Copia analítica del Índice de Cumplimiento (ADR-100). Es derivada de MongoDB, que sigue siendo la única fuente de verdad:
-- si esta base se borra, se reconstruye con la siguiente copia. No guarda datos personales: solo cortes, barrios y duraciones.
CREATE SCHEMA IF NOT EXISTS analitica;

CREATE TABLE analitica.sector (
    id          TEXT PRIMARY KEY,                       -- el slug de Mongo (p. ej. 'albornoz')
    nombre      TEXT        NOT NULL,
    poblacion   INTEGER     CHECK (poblacion >= 0),     -- nulo: 27 barrios no tienen dato censal (un 0 sería un barrio sin habitantes)
    copiado_en  TIMESTAMPTZ NOT NULL
);

CREATE TABLE analitica.corte (
    id             TEXT PRIMARY KEY,                    -- el id del corte en Mongo
    origen         TEXT        NOT NULL,                -- OFICIAL_ACUACAR, INGESTA_IA, VEEDOR
    estado         TEXT        NOT NULL,                -- ANUNCIADO, CONFIRMADO, RESTABLECIDO, EXPIRADO, ANULADO
    causa          TEXT,
    inicio         TIMESTAMPTZ NOT NULL,
    fin_prometido  TIMESTAMPTZ NOT NULL,
    copiado_en     TIMESTAMPTZ NOT NULL,
    CHECK (fin_prometido > inicio)
);

-- Una fila por barrio afectado (par corte-barrio, D14). El cierre es opcional: un barrio sin cierre todavía no cuenta en el Índice.
CREATE TABLE analitica.corte_sector (
    corte_id            TEXT        NOT NULL REFERENCES analitica.corte (id) ON DELETE CASCADE,
    sector_id           TEXT        NOT NULL REFERENCES analitica.sector (id),
    cierre_hora         TIMESTAMPTZ,
    cierre_fuente       TEXT,
    cierre_provisional  BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (corte_id, sector_id),
    CHECK (cierre_hora IS NOT NULL OR (cierre_fuente IS NULL AND NOT cierre_provisional))
);
CREATE INDEX corte_sector_por_sector ON analitica.corte_sector (sector_id);
CREATE INDEX corte_sector_por_cierre ON analitica.corte_sector (cierre_hora) WHERE cierre_hora IS NOT NULL;

-- Una fila por cada copia: cuándo, cuánto y si los conteos cuadraron con Mongo.
CREATE TABLE analitica.copia (
    id                 BIGSERIAL   PRIMARY KEY,
    iniciada_en        TIMESTAMPTZ NOT NULL,
    terminada_en       TIMESTAMPTZ,
    resultado          TEXT        NOT NULL CHECK (resultado IN ('EN_CURSO', 'OK', 'CON_DIFERENCIAS', 'FALLIDA')),
    sectores_en_mongo  INTEGER,
    sectores_en_sql    INTEGER,
    cortes_en_mongo    INTEGER,
    cortes_en_sql      INTEGER,
    pares_en_mongo     INTEGER,
    pares_en_sql       INTEGER,
    detalle            TEXT
);
