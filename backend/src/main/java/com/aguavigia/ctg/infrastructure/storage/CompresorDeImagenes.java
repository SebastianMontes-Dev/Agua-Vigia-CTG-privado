package com.aguavigia.ctg.infrastructure.storage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * RNF021 — compresión automática y limpieza de metadatos EXIF (M10). Decodificar la imagen y
 * volver a codificarla logra las dos cosas a la vez: el escritor de ImageIO no copia el EXIF del
 * original salvo que se le entregue explícitamente como metadato, así que la recodificación lo
 * descarta sin ningún paso extra. Es lo que le devuelve al vecino la garantía de que una foto
 * subida con GPS embebido no expone una ubicación más precisa que la que autorizó en RF007.
 *
 * Limitado a JPEG y PNG: el ImageIO del JDK no trae lector de WebP sin un plugin externo
 * (p. ej. TwelveMonkeys imageio-webp), y un WebP guardado tal cual conservaría su EXIF. Por eso
 * AgregarEvidenciaService rechaza WebP antes de llegar aquí: este compresor solo recibe formatos
 * que sabe limpiar.
 */
final class CompresorDeImagenes {

    private static final int LADO_MAXIMO_PX = 1600;
    private static final float CALIDAD_JPEG = 0.75f;
    /** 25 megapíxeles: más que cualquier cámara de teléfono corriente. Más que esto se rechaza sin decodificar. */
    private static final long MAXIMO_DE_PIXELES = 25_000_000L;
    /** Una PNG no pierde calidad al recodificarse, así que una de ruido pesa megas aunque se reduzca a 1600 px. */
    private static final int MAXIMO_PNG_PROCESADA_BYTES = 3 * 1024 * 1024;

    private CompresorDeImagenes() {
    }

    static byte[] recomprimir(String extension, byte[] original) {
        String formato = switch (extension) {
            case ".jpg" -> "jpg";
            case ".png" -> "png";
            default -> null; // una extensión futura que ImageIO no soporte
        };
        if (formato == null) {
            return original;
        }

        try {
            exigirDimensionesRazonables(original);
            BufferedImage decodificada = ImageIO.read(new ByteArrayInputStream(original));
            if (decodificada == null) {
                // AgregarEvidenciaService ya verificó la firma binaria antes de llegar aquí: si con
                // eso ImageIO igual no puede decodificarla, no es un caso de borde de formato — es
                // un archivo corrupto o construido a mano para pasar la firma sin ser una imagen
                // real. Guardar el original sin comprimir (como antes) lo hubiera alojado tal cual
                // en el almacén de fotos, sin ninguna otra verificación de contenido.
                throw new IllegalArgumentException(
                        "El archivo declarado como imagen no se pudo decodificar: no es una imagen válida.");
            }
            BufferedImage redimensionada = redimensionarSiExcede(decodificada, LADO_MAXIMO_PX);
            byte[] procesada = codificar(redimensionada, formato);
            if ("png".equals(formato) && procesada.length > MAXIMO_PNG_PROCESADA_BYTES) {
                throw new IllegalArgumentException("La imagen PNG pesa demasiado incluso reducida: envía una foto JPEG o una PNG más simple.");
            }
            return procesada;
        } catch (IOException e) {
            throw new UncheckedIOException("Error al comprimir la imagen antes de guardarla", e);
        }
    }

    /**
     * Las dimensiones están en la cabecera: se leen sin decodificar. Una imagen de 10 MB puede declarar miles de millones de
     * píxeles (una PNG de un color comprime ~1000:1) y decodificarla agotaría la memoria.
     */
    private static void exigirDimensionesRazonables(byte[] original) throws IOException {
        try (var flujo = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<javax.imageio.ImageReader> lectores = flujo == null ? null : ImageIO.getImageReaders(flujo);
            if (lectores == null || !lectores.hasNext()) {
                throw new IllegalArgumentException(
                        "El archivo declarado como imagen no se pudo decodificar: no es una imagen válida.");
            }
            javax.imageio.ImageReader lector = lectores.next();
            try {
                lector.setInput(flujo, true, true);
                long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
                if (pixeles > MAXIMO_DE_PIXELES) {
                    throw new IllegalArgumentException("La imagen tiene demasiados píxeles (%d): el máximo es %d."
                            .formatted(pixeles, MAXIMO_DE_PIXELES));
                }
            } finally {
                lector.dispose();
            }
        }
    }

    private static BufferedImage redimensionarSiExcede(BufferedImage original, int ladoMaximo) {
        int ancho = original.getWidth();
        int alto = original.getHeight();
        if (ancho <= ladoMaximo && alto <= ladoMaximo) {
            return original;
        }

        double escala = ladoMaximo / (double) Math.max(ancho, alto);
        int nuevoAncho = Math.max(1, (int) Math.round(ancho * escala));
        int nuevoAlto = Math.max(1, (int) Math.round(alto * escala));

        BufferedImage redimensionada = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = redimensionada.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // drawImage directo con interpolación bilineal: getScaledInstance(SCALE_SMOOTH) es lento y gasta mucha memoria.
            g2d.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
        } finally {
            g2d.dispose();
        }
        return redimensionada;
    }

    private static byte[] codificar(BufferedImage imagen, String formato) throws IOException {
        if ("jpg".equals(formato)) {
            return codificarJpegConCalidad(imagen, CALIDAD_JPEG);
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, formato, salida);
        return salida.toByteArray();
    }

    private static byte[] codificarJpegConCalidad(BufferedImage imagen, float calidad) throws IOException {
        Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter escritor = escritores.next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(calidad);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream flujo = new MemoryCacheImageOutputStream(salida)) {
            escritor.setOutput(flujo);
            // El escritor JPEG no acepta canal alfa ("Bogus input colorspace"); una imagen PNG
            // decodificada como ARGB necesita aplanarse antes de codificar, aunque no se haya
            // redimensionado (redimensionarSiExcede solo normaliza a RGB cuando sí redimensiona).
            escritor.write(null, new IIOImage(aRgbSinAlfa(imagen), null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    private static BufferedImage aRgbSinAlfa(BufferedImage imagen) {
        if (imagen.getType() == BufferedImage.TYPE_INT_RGB) {
            return imagen;
        }
        BufferedImage rgb = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgb.createGraphics();
        try {
            g2d.drawImage(imagen, 0, 0, null);
        } finally {
            g2d.dispose();
        }
        return rgb;
    }
}
