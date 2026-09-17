package com.aguavigia.ctg.application;

/**
 * Verifica los primeros bytes de un archivo contra la firma binaria real de su formato, en vez de
 * confiar en el `Content-Type` que declaró el cliente (ver AgregarEvidenciaService). Cubre
 * exactamente los tres tipos de `TIPOS_PERMITIDOS`: no es un validador de imágenes de propósito
 * general.
 */
final class FirmaDeImagen {

    private static final int[] JPEG = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int[] RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final int[] WEBP = {0x57, 0x45, 0x42, 0x50}; // "WEBP"

    private FirmaDeImagen() {
    }

    static boolean coincideConTipo(String contentType, byte[] contenido) {
        if (contenido == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> tieneFirmaEn(contenido, 0, JPEG);
            case "image/png" -> tieneFirmaEn(contenido, 0, PNG);
            // WEBP es un contenedor RIFF: "RIFF" en el byte 0, 4 bytes de tamaño (no se validan),
            // "WEBP" en el byte 8. No hay decodificador en este backend que verifique más adentro.
            case "image/webp" -> tieneFirmaEn(contenido, 0, RIFF) && tieneFirmaEn(contenido, 8, WEBP);
            default -> false;
        };
    }

    private static boolean tieneFirmaEn(byte[] contenido, int offset, int[] firma) {
        if (contenido.length < offset + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((contenido[offset + i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
