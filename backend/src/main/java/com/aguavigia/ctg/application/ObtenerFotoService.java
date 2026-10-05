package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.FotoLeida;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.port.in.ObtenerFotoUseCase;
import com.aguavigia.ctg.domain.port.out.AlmacenamientoPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * La foto es evidencia para el veedor, no contenido público: antes se servía todo el directorio de fotos sin mirar la
 * moderación. Ahora quien sirve decide por el reporte dueño de la foto.
 */
public class ObtenerFotoService implements ObtenerFotoUseCase {

    /** El nombre que el servidor generó: un UUID y su extensión. Cualquier otra cosa no se busca ni se lee. */
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("[A-Za-z0-9-]+\\.(jpg|png)");

    private final ReporteCiudadanoRepository reportes;
    private final AlmacenamientoPort almacenamiento;

    public ObtenerFotoService(ReporteCiudadanoRepository reportes, AlmacenamientoPort almacenamiento) {
        this.reportes = reportes;
        this.almacenamiento = almacenamiento;
    }

    @Override
    public Optional<FotoLeida> paraPublico(String nombre) {
        return obtener(nombre, ReporteCiudadano::fotoEsPublica);
    }

    @Override
    public Optional<FotoLeida> paraPanel(String nombre) {
        return obtener(nombre, reporte -> true);
    }

    private Optional<FotoLeida> obtener(String nombre, Predicate<ReporteCiudadano> puedeVerse) {
        if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches()) {
            return Optional.empty();
        }
        return reportes.buscarPorNombreDeFoto(nombre)
                .filter(puedeVerse)
                .flatMap(reporte -> almacenamiento.leer(nombre))
                .map(bytes -> new FotoLeida(bytes, nombre.endsWith(".png") ? "image/png" : "image/jpeg"));
    }
}
