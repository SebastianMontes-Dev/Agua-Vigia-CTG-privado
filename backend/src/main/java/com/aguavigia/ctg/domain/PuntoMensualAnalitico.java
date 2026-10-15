package com.aguavigia.ctg.domain;

import java.time.YearMonth;

/** El Índice de un mes (en hora de Cartagena), según la vista SQL. */
public record PuntoMensualAnalitico(YearMonth mes, long paresCerrados, long prometidaSegundos, long realSegundos,
                                    double porcentajeCumplimiento) {
}
