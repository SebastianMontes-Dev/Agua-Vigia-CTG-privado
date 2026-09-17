package com.aguavigia.ctg.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlmacenamientoLocalAdapterTest {

    private static final com.aguavigia.ctg.domain.port.out.RelojPort RELOJ =
            java.time.Instant::now;

    @TempDir
    Path tempDir;

    @Test
    void debeGuardarElArchivoYDevolverUnaUrlBajoFotos() throws Exception {
        AlmacenamientoLocalAdapter adaptador = new AlmacenamientoLocalAdapter(tempDir.toString(), RELOJ);

        String url = adaptador.guardar(".jpg", jpegDePrueba());

        assertThat(url).startsWith("/fotos/").endsWith(".jpg");
        String nombreArchivo = url.substring("/fotos/".length());
        Path guardado = tempDir.resolve(nombreArchivo);
        assertThat(Files.exists(guardado)).isTrue();
        assertThat(Files.size(guardado)).isGreaterThan(0);
    }

    @Test
    void noDebeUsarNombreDeArchivoDelCliente_soloLaExtensionValidada() throws Exception {
        AlmacenamientoLocalAdapter adaptador = new AlmacenamientoLocalAdapter(tempDir.toString(), RELOJ);

        String url = adaptador.guardar(".png", pngDePrueba());

        assertThat(url).doesNotContain("<script>").endsWith(".png");
    }

    @Test
    void listarNombresConAntiguedadMinima_soloDebeIncluirArchivosMasViejosQueElUmbral() throws Exception {
        AlmacenamientoLocalAdapter adaptador = new AlmacenamientoLocalAdapter(tempDir.toString(), RELOJ);
        Path viejo = tempDir.resolve("viejo.jpg");
        Path nuevo = tempDir.resolve("nuevo.jpg");
        Files.write(viejo, new byte[]{1});
        Files.write(nuevo, new byte[]{2});
        Files.setLastModifiedTime(viejo, FileTime.from(Instant.now().minus(Duration.ofDays(2))));

        Set<String> candidatos = adaptador.listarNombresConAntiguedadMinima(Duration.ofHours(24));

        assertThat(candidatos).containsExactly("viejo.jpg");
    }

    @Test
    void eliminar_debeBorrarElArchivo() throws Exception {
        AlmacenamientoLocalAdapter adaptador = new AlmacenamientoLocalAdapter(tempDir.toString(), RELOJ);
        Path archivo = tempDir.resolve("borrar-este.jpg");
        Files.write(archivo, new byte[]{1});

        adaptador.eliminar("borrar-este.jpg");

        assertThat(Files.exists(archivo)).isFalse();
    }

    @Test
    void eliminar_esIdempotenteSiElArchivoYaNoExiste() {
        AlmacenamientoLocalAdapter adaptador = new AlmacenamientoLocalAdapter(tempDir.toString(), RELOJ);

        assertThatCode(() -> adaptador.eliminar("nunca-existio.jpg")).doesNotThrowAnyException();
    }

    @Test
    void eliminar_debeRechazarNombresQueIntentenEscaparDelDirectorio() {
        AlmacenamientoLocalAdapter adaptador = new AlmacenamientoLocalAdapter(tempDir.toString(), RELOJ);

        assertThatThrownBy(() -> adaptador.eliminar("../fuera.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // CompresorDeImagenes ahora rechaza contenido que no decodifica de verdad (ver su propia
    // suite) — estas pruebas de bajo nivel del adaptador necesitan una imagen real, no bytes
    // arbitrarios, para llegar hasta la escritura en disco.
    private static byte[] jpegDePrueba() throws IOException {
        return aBytes(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    private static byte[] pngDePrueba() throws IOException {
        return aBytes(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png");
    }

    private static byte[] aBytes(BufferedImage imagen, String formato) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, formato, salida);
        return salida.toByteArray();
    }
}
