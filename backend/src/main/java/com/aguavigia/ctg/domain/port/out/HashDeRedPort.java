package com.aguavigia.ctg.domain.port.out;

import java.time.LocalDate;

/**
 * Resume la red desde la que llega un reporte sin guardar la IP. El resumen cambia cada día: sirve para saber, el
 * mismo día, si dos reportes salen de la misma red (el quórum exige varias), pero no para seguir a una red de un
 * día a otro.
 */
public interface HashDeRedPort {

    String hashear(String ip, LocalDate dia);
}
