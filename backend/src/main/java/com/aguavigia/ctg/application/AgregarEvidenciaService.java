package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.port.in.AgregarEvidenciaUseCase;
import com.aguavigia.ctg.domain.port.out.AlmacenamientoPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;

import java.util.Map;

public class AgregarEvidenciaService implements AgregarEvidenciaUseCase {

    /**
     * M10: solo imagenes. La extension sale de aqui, nunca del nombre de archivo que manda el
     * cliente — si mas adelante se sirve el directorio de fotos, un ".svg" o ".html" subido con
     * un nombre falsificado se hubiera convertido en XSS almacenado del mismo origen.
     *
     * Sin WebP: el JDK no trae decodificador (ver CompresorDeImagenes), así que no se podría
     * recomprimir ni quitarle el EXIF, y se guardaría tal cual con la ubicación del teléfono.
     */
    private static final Map<String, String> TIPOS_PERMITIDOS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png");

    private final ReporteCiudadanoRepository reportes;
    private final AlmacenamientoPort almacenamiento;

    public AgregarEvidenciaService(ReporteCiudadanoRepository reportes, AlmacenamientoPort almacenamiento) {
        this.reportes = reportes;
        this.almacenamiento = almacenamiento;
    }

    @Override
    public ReporteCiudadano agregarEvidencia(String reporteId, String contentType, byte[] contenido) {
        ReporteId id = new ReporteId(reporteId);
        ReporteCiudadano reporte = reportes.buscarPorId(id)
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe el reporte '" + reporteId + "'"));

        // La subida es pública y sin dueño: quien conociera el id de un reporte ajeno podría cambiarle la foto.
        // Una vez puesta, la evidencia no se reemplaza.
        if (reporte.fotoUrl() != null) {
            throw new IllegalStateException("El reporte '" + reporteId + "' ya tiene una foto de evidencia.");
        }

        String extension = contentType == null ? null : TIPOS_PERMITIDOS.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido: '%s'. Solo se aceptan %s.".formatted(contentType, TIPOS_PERMITIDOS.keySet()));
        }
        // El Content-Type es un dato que el cliente declara, no que el archivo demuestra
        // (`POST /api/reportes/{id}/foto` es publico, sin cuenta). Sin esta verificacion, cualquier
        // binario declarado con un tipo permitido se guardaba y servia tal cual bajo /fotos/**.
        if (!FirmaDeImagen.coincideConTipo(contentType, contenido)) {
            throw new IllegalArgumentException(
                    "El archivo no es un '%s' válido: sus primeros bytes no coinciden con el formato declarado."
                            .formatted(contentType));
        }

        String url = almacenamiento.guardar(extension, contenido);
        ReporteCiudadano conFoto = reporte.conFoto(url);
        return reportes.guardar(conFoto);
    }
}
