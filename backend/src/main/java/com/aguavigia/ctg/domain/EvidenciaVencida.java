package com.aguavigia.ctg.domain;

/**
 * Proyección mínima para PurgaEvidenciaAntiguaJob: solo lo que hace falta para borrar el archivo
 * en disco y luego limpiar `fotoUrl` en lote. Traer el {@link ReporteCiudadano} completo (sector,
 * tipo, moderación, confirmaciones) para esto era sobre-fetch puro — el job nunca los usa.
 */
public record EvidenciaVencida(ReporteId id, String fotoUrl) {
}
