package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.EvidenciaVencida;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.RedEnRafaga;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.VotoReciente;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ReporteCiudadanoRepository {

    ReporteCiudadano guardar(ReporteCiudadano reporte);

    /**
     * D29 — el reporte más reciente de cada identidad en cada barrio desde {@code desde}, sin lo que el veedor descartó:
     * lo que el contador de Redis tendría si nunca se hubiera vaciado.
     */
    List<VotoReciente> votosRecientes(Instant desde);

    /** RF009-RF011 — sustento del consenso: excluye lo que el veedor ya descartó como spam. */
    List<ReporteCiudadano> listarRecientesPorSector(SectorId sectorId, Duration ventana);

    /**
     * RF009 — cuántos vecinos distintos votan por cada tipo de reporte en la ventana, sin traer los
     * reportes: cada dispositivo cuenta una vez, con su reporte MÁS RECIENTE, y se excluye lo que el
     * veedor descartó. Es lo que decide si hay consenso y hacia dónde; durante una avería masiva un
     * sector acumula miles de reportes y cargarlos todos en cada POST agotaba el pool de conexiones.
     */
    Map<TipoReporte, Long> contarVotosRecientes(SectorId sectorId, Duration ventana);

    /**
     * RF006 — cupo de reportes por dispositivo. Cuenta TODO lo que el dispositivo envió en la
     * ventana, sin filtrar por moderación: que el veedor descarte un reporte de un abusador no
     * puede reiniciarle el cupo (BUG-041).
     */
    long contarRecientesPorSectorYDispositivo(SectorId sectorId, Duration ventana, HuellaDispositivo huella);

    Optional<ReporteCiudadano> buscarPorId(ReporteId id);

    /**
     * El reporte dueño de esa foto, sin importar su estado de moderación: quien sirve la foto decide si es pública.
     * {@code nombre} es solo el nombre del archivo; coincide con el final exacto de la URL guardada, sea la vieja
     * (`/fotos/x.jpg`) o la nueva (`/api/fotos/x.jpg`).
     */
    Optional<ReporteCiudadano> buscarPorNombreDeFoto(String nombre);

    /**
     * Pone la foto solo si el reporte aún no tiene ninguna, sin tocar nada más. No es leer→modificar→guardar: una
     * aprobación o una confirmación simultáneas no quedan pisadas por el documento viejo.
     *
     * @return false si el reporte no existe o ya tenía foto
     */
    boolean asignarFotoSiNoTiene(ReporteId id, String fotoUrl, String fotoSha256);

    /** Marca la foto como descartada sin tocar el resto del reporte. @return false si el reporte no existe o no tiene foto */
    boolean marcarFotoDescartada(ReporteId id);

    /**
     * RF018 — la cola de moderación del veedor, paginada: en una jornada sin veedor disponible la
     * cola acumula todo lo que reportó la ciudad, y traerla entera la vuelve inmanejable justo
     * cuando más grande es.
     */
    Pagina<ReporteCiudadano> listarPendientes(int pagina, int tamano);

    /**
     * D9 — las redes que enviaron al menos {@code minimo} reportes a un mismo barrio desde {@code desde}, de entre los
     * barrios dados. Cuenta todo lo enviado, también lo ya moderado: descartar un reporte de la ráfaga no la borra.
     * Ignora los reportes sin red conocida (sensores, anteriores a D9).
     */
    Set<RedEnRafaga> redesEnRafaga(Collection<SectorId> sectores, Instant desde, int minimo);

    /**
     * Nombres de archivo (no URLs) de toda foto referenciada por algún reporte, sin importar su
     * estado de moderación — un reporte DESCARTADO sigue siendo dueño legítimo de su foto en
     * disco; eso no es una foto huérfana (ver LimpiezaFotosHuerfanasJob).
     */
    Set<String> listarNombresDeFotoReferenciados();

    /**
     * Solo id + fotoUrl de los reportes con foto cuyo timestamp es anterior a {@code limite} —
     * candidatos a purga de evidencia por retención. Proyección, no {@link ReporteCiudadano}
     * completo: el único consumidor (PurgaEvidenciaAntiguaJob) no necesita el resto de los campos.
     */
    List<EvidenciaVencida> listarEvidenciaAnteriorA(Instant limite);

    /** Limpia `fotoUrl` de todos los reportes indicados en una sola operación (retención). */
    void quitarFotosDe(List<ReporteId> ids);
}
