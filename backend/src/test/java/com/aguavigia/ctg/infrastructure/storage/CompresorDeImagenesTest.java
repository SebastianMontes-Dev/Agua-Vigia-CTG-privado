package com.aguavigia.ctg.infrastructure.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RNF021 — compresión y limpieza de EXIF (M10). La limpieza de EXIF en sí no se verifica con un
 * metadato plantado (ImageIO no ofrece una forma simple de fabricar uno en una prueba): se apoya
 * en el comportamiento documentado de ImageIO de no copiar metadatos del original al recodificar,
 * que es justo lo que estas pruebas ejercitan indirectamente al forzar la recodificación.
 */
class CompresorDeImagenesTest {

    @Test
    void debeRedimensionarUnaImagenQueExcedeElLadoMaximo() throws IOException {
        byte[] original = jpegDe(2000, 1000);

        byte[] resultado = CompresorDeImagenes.recomprimir(".jpg", original);

        BufferedImage decodificado = ImageIO.read(new ByteArrayInputStream(resultado));
        assertThat(decodificado.getWidth()).isEqualTo(1600);
        assertThat(decodificado.getHeight()).isEqualTo(800);
    }

    @Test
    void noDebeRedimensionarUnaImagenDentroDelLimite() throws IOException {
        byte[] original = jpegDe(400, 300);

        byte[] resultado = CompresorDeImagenes.recomprimir(".jpg", original);

        BufferedImage decodificado = ImageIO.read(new ByteArrayInputStream(resultado));
        assertThat(decodificado.getWidth()).isEqualTo(400);
        assertThat(decodificado.getHeight()).isEqualTo(300);
    }

    @Test
    void debeReducirElPesoDeUnaImagenRuidosaGrande() throws IOException {
        byte[] original = jpegRuidosoDe(1800, 1800);

        byte[] resultado = CompresorDeImagenes.recomprimir(".jpg", original);

        assertThat(resultado.length).isLessThan(original.length);
    }

    @Test
    void debeAplanarUnPngConTransparenciaAntesDeCodificarComoJpg() throws IOException {
        byte[] original = pngArgbDe(300, 200);

        byte[] resultado = CompresorDeImagenes.recomprimir(".jpg", original);

        BufferedImage decodificado = ImageIO.read(new ByteArrayInputStream(resultado));
        assertThat(decodificado).isNotNull();
        assertThat(decodificado.getWidth()).isEqualTo(300);
    }

    @Test
    void debeRecodificarUnPngComoPng() throws IOException {
        byte[] original = pngArgbDe(2000, 300);

        byte[] resultado = CompresorDeImagenes.recomprimir(".png", original);

        BufferedImage decodificado = ImageIO.read(new ByteArrayInputStream(resultado));
        assertThat(decodificado.getWidth()).isEqualTo(1600);
    }

    /** Una extensión que ImageIO no lee se devuelve intacta, no como error (WebP ya no llega aquí: se rechaza antes). */
    @Test
    void debeDejarWebpSinModificar() {
        byte[] original = {1, 2, 3, 4};

        byte[] resultado = CompresorDeImagenes.recomprimir(".webp", original);

        assertThat(resultado).isSameAs(original);
    }

    /**
     * Antes este caso guardaba `basura` tal cual bajo /fotos/**, sin comprimir ni verificar nada
     * más allá del Content-Type ya validado en AgregarEvidenciaService. Si ImageIO no puede
     * decodificarlo pese a eso, no es un caso de borde de formato: se rechaza.
     */
    /**
     * Una PNG de un solo color pesa pocos KB pero declara miles de millones de píxeles: decodificarla reservaría gigas de
     * memoria y, con ExitOnOutOfMemoryError, tumbaría el proceso. Las dimensiones se leen de la cabecera y se rechazan antes.
     */
    @Test
    void unaImagenConDemasiadosPixelesSeRechazaSinDecodificarla() throws IOException {
        BufferedImage enorme = new BufferedImage(6000, 6000, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(enorme, "png", png);
        assertThat(png.size()).as("pesa poco aunque declare 36 megapíxeles").isLessThan(1_000_000);

        assertThatThrownBy(() -> CompresorDeImagenes.recomprimir(".png", png.toByteArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("píxeles");
    }

    /** Una PNG de ruido a 1600 px no pierde calidad al recodificarse y pesa ~7 MB: sin tope, cada foto llenaría el disco. */
    @Test
    void unaPngQueSigueSiendoPesadaTrasProcesarseSeRechaza() throws IOException {
        BufferedImage ruido = new BufferedImage(1600, 1600, BufferedImage.TYPE_INT_RGB);
        Random azar = new Random(7);
        for (int y = 0; y < 1600; y++) {
            for (int x = 0; x < 1600; x++) {
                ruido.setRGB(x, y, azar.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(ruido, "png", png);

        assertThatThrownBy(() -> CompresorDeImagenes.recomprimir(".png", png.toByteArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pesa demasiado");
    }

    @Test
    void unContenidoNoDecodificableDebeRechazarse() {
        byte[] basura = {9, 9, 9};

        assertThatThrownBy(() -> CompresorDeImagenes.recomprimir(".jpg", basura))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] jpegDe(int ancho, int alto) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        return aBytes(imagen, "jpg");
    }

    private static byte[] jpegRuidosoDe(int ancho, int alto) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Random aleatorio = new Random(42);
        for (int x = 0; x < ancho; x++) {
            for (int y = 0; y < alto; y++) {
                imagen.setRGB(x, y, aleatorio.nextInt());
            }
        }
        // Calidad maxima para que el original pese mucho mas que la recompresion a 0.75.
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        var escritores = ImageIO.getImageWritersByFormatName("jpg");
        var escritor = escritores.next();
        var parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(1.0f);
        try (var flujo = new javax.imageio.stream.MemoryCacheImageOutputStream(salida)) {
            escritor.setOutput(flujo);
            escritor.write(null, new javax.imageio.IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    private static byte[] pngArgbDe(int ancho, int alto) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        return aBytes(imagen, "png");
    }

    private static byte[] aBytes(BufferedImage imagen, String formato) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, formato, salida);
        return salida.toByteArray();
    }
}
