package com.aguavigia.ctg.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirmaDeImagenTest {

    private static byte[] bytes(int... valores) {
        byte[] resultado = new byte[valores.length];
        for (int i = 0; i < valores.length; i++) {
            resultado[i] = (byte) valores[i];
        }
        return resultado;
    }

    @Test
    void debeAceptarUnJpegConSuFirmaReal() {
        assertThat(FirmaDeImagen.coincideConTipo("image/jpeg", bytes(0xFF, 0xD8, 0xFF, 0, 0)))
                .isTrue();
    }

    @Test
    void debeAceptarUnPngConSuFirmaReal() {
        assertThat(FirmaDeImagen.coincideConTipo("image/png",
                bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)))
                .isTrue();
    }

    @Test
    void debeAceptarUnWebpConSuContenedorRiffReal() {
        // "RIFF" + 4 bytes de tamaño (no se validan) + "WEBP"
        assertThat(FirmaDeImagen.coincideConTipo("image/webp",
                bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')))
                .isTrue();
    }

    @Test
    void debeRechazarUnJpegDeclaradoQueEnRealidadEsOtraCosa() {
        assertThat(FirmaDeImagen.coincideConTipo("image/jpeg", "no soy una imagen".getBytes()))
                .isFalse();
    }

    @Test
    void debeRechazarUnWebpDeclaradoSinLaMarcaWebpEnElContenedorRiff() {
        // Contenedor RIFF válido, pero de otro formato (ej. WAV) — exactamente lo que este chequeo
        // existe para atrapar: declarar "image/webp" no basta si el contenido no lo es de verdad.
        assertThat(FirmaDeImagen.coincideConTipo("image/webp",
                bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E')))
                .isFalse();
    }

    @Test
    void debeRechazarContenidoDemasiadoCortoParaLlevarLaFirma() {
        assertThat(FirmaDeImagen.coincideConTipo("image/png", bytes(0x89, 0x50))).isFalse();
    }

    @Test
    void debeRechazarUnTipoNoContemplado() {
        assertThat(FirmaDeImagen.coincideConTipo("image/svg+xml", bytes(0xFF, 0xD8, 0xFF))).isFalse();
    }
}
