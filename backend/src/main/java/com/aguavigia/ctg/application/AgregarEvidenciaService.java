package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.FormatoNoPermitidoException;
import com.aguavigia.ctg.domain.FotoGuardada;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SubidaNoAutorizadaException;
import com.aguavigia.ctg.domain.port.in.AgregarEvidenciaUseCase;
import com.aguavigia.ctg.domain.port.out.AlmacenamientoPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SubidaDeFotoRepository;
import com.aguavigia.ctg.domain.port.out.TokenDeSubidaPort;

import java.util.Map;

/**
 * La subida es pública y sin cuenta, así que lo que la autoriza es el token que solo recibió el autor del reporte (D10):
 * de un solo uso y atado a ese reporte. Conocer el id de un reporte ajeno —es público en la bitácora— no sirve de nada.
 *
 * El orden importa: primero se comprueba el archivo y después se gasta el token, para que una foto rechazada no deje al
 * autor sin poder reintentar; y el token se consume antes de mirar el reporte, para que «no existe» y «token ajeno»
 * respondan lo mismo.
 */
public class AgregarEvidenciaService implements AgregarEvidenciaUseCase {

    /**
     * Solo imágenes. La extensión sale de aquí, nunca del nombre de archivo que manda el cliente: un ".svg" o ".html"
     * subido con un nombre falsificado se hubiera convertido en XSS almacenado del mismo origen.
     *
     * Sin WebP: el JDK no trae decodificador (ver CompresorDeImagenes), así que no se podría recomprimir ni quitarle el
     * EXIF, y se guardaría tal cual con la ubicación del teléfono.
     */
    private static final Map<String, String> TIPOS_PERMITIDOS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png");

    private final ReporteCiudadanoRepository reportes;
    private final AlmacenamientoPort almacenamiento;
    private final SubidaDeFotoRepository subidas;
    private final TokenDeSubidaPort tokens;
    private final RelojPort reloj;

    public AgregarEvidenciaService(ReporteCiudadanoRepository reportes, AlmacenamientoPort almacenamiento,
                                   SubidaDeFotoRepository subidas, TokenDeSubidaPort tokens, RelojPort reloj) {
        this.reportes = reportes;
        this.almacenamiento = almacenamiento;
        this.subidas = subidas;
        this.tokens = tokens;
        this.reloj = reloj;
    }

    @Override
    public ReporteCiudadano agregarEvidencia(String reporteId, String tokenDeSubida, String contentType,
                                             byte[] contenido) {
        if (tokenDeSubida == null || tokenDeSubida.isBlank()) {
            throw noAutorizada();
        }
        String extension = contentType == null ? null : TIPOS_PERMITIDOS.get(contentType);
        if (extension == null) {
            throw new FormatoNoPermitidoException(
                    "Tipo de archivo no permitido: '%s'. Solo se aceptan %s.".formatted(contentType, TIPOS_PERMITIDOS.keySet()));
        }
        // El Content-Type es un dato que el cliente declara, no que el archivo demuestra. Sin esta verificación,
        // cualquier binario declarado con un tipo permitido se guardaba y se servía tal cual.
        if (!FirmaDeImagen.coincideConTipo(contentType, contenido)) {
            throw new IllegalArgumentException(
                    "El archivo no es un '%s' válido: sus primeros bytes no coinciden con el formato declarado."
                            .formatted(contentType));
        }

        ReporteId id = new ReporteId(reporteId);
        if (!subidas.consumir(id, tokens.hash(tokenDeSubida), reloj.ahora())) {
            throw noAutorizada();
        }

        try {
            ReporteCiudadano reporte = reportes.buscarPorId(id)
                    .orElseThrow(() -> new EntidadNoEncontradaException("No existe el reporte '" + reporteId + "'"));
            // Una vez puesta, la evidencia no se reemplaza.
            if (reporte.fotoUrl() != null) {
                throw new IllegalStateException("El reporte '" + reporteId + "' ya tiene una foto de evidencia.");
            }

            FotoGuardada guardada = almacenamiento.guardar(extension, contenido);
            // Solo el campo de la foto, atómicamente y solo si sigue sin tener: guardar el documento entero podía
            // revertir una aprobación o una confirmación que llegaron entre la lectura y la escritura.
            if (!reportes.asignarFotoSiNoTiene(id, guardada.url(), guardada.sha256())) {
                throw new IllegalStateException("El reporte '" + reporteId + "' ya tiene una foto de evidencia.");
            }
            return reportes.buscarPorId(id)
                    .orElseThrow(() -> new EntidadNoEncontradaException("No existe el reporte '" + reporteId + "'"));
        } catch (IllegalStateException | EntidadNoEncontradaException noHayNadaQueReintentar) {
            throw noHayNadaQueReintentar;
        } catch (RuntimeException fallo) {
            // El token ya se gastó. Si lo que falló fue procesar la imagen (corrupta, error de disco), el autor no
            // tiene otro token: se le devuelve uno por un plazo corto para que pueda reintentar con otra foto.
            subidas.guardar(id, tokens.hash(tokenDeSubida), reloj.ahora().plus(PLAZO_PARA_REINTENTAR));
            throw fallo;
        }
    }

    /** Lo que se le devuelve al autor si el procesado falló tras gastar su token. */
    private static final java.time.Duration PLAZO_PARA_REINTENTAR = java.time.Duration.ofMinutes(2);

    private static SubidaNoAutorizadaException noAutorizada() {
        return new SubidaNoAutorizadaException(
                "Para subir la foto hace falta el token que recibiste al reportar: se usa una sola vez y vence a los pocos minutos.");
    }
}
