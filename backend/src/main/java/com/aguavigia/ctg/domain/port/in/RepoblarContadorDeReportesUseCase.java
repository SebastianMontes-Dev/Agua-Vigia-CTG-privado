package com.aguavigia.ctg.domain.port.in;

/**
 * D29 — al arrancar, devuelve al contador de Redis los reportes de la ventana que ya están en Mongo. Sin esto, un
 * Redis vaciado o un reinicio dejan al contador en cero y el primer reporte nuevo no alcanza el listón aunque los
 * demás votos del quórum ya estén guardados.
 */
public interface RepoblarContadorDeReportesUseCase {

    /** @return cuántos votos devolvió al contador */
    int repoblar();
}
