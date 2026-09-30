package com.aguavigia.ctg.application;

/**
 * Verifica los primeros bytes de un archivo contra la firma binaria real de su formato, en vez de
 * confiar en el `Content-Type` que declaró el cliente (ver AgregarEvidenciaService). Cubre
 * exactamente los dos tipos de `TIPOS_PERMITIDOS`: no es un validador de imágenes de propósito
 * general.
 */
final class FirmaDeImagen {

    private static final int[] JPEG = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private FirmaDeImagen() {
    }

    static boolean coincideConTipo(String contentType, byte[] contenido) {
        if (contenido == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> tieneFirmaEn(contenido, 0, JPEG);
            case "image/png" -> tieneFirmaEn(contenido, 0, PNG);
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
