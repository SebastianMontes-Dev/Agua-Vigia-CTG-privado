package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.FotoGuardada;

import java.time.Duration;
import java.util.Set;

public interface AlmacenamientoPort {

    /**
     * {@code extension} incluye el punto (p. ej. ".jpg"), ya validada por el llamador. Devuelve dónde se sirve la foto y
     * el SHA-256 de lo que quedó en disco (ya recomprimido y sin EXIF).
     */
    FotoGuardada guardar(String extension, byte[] contenido);

    /**
     * Los bytes de la foto, o vacío si no existe o el nombre intenta salir de la carpeta. {@code nombre} es solo el nombre
     * del archivo, nunca una ruta.
     */
    java.util.Optional<byte[]> leer(String nombre);

    /**
     * Nombres de archivo (no URLs) con antiguedad mayor o igual a la indicada — usado por
     * LimpiezaFotosHuerfanasJob para no considerar candidato un archivo cuyo reporte asociado
     * todavia esta en vuelo de guardarse.
     */
    Set<String> listarNombresConAntiguedadMinima(Duration antiguedadMinima);

    /** Idempotente: si el archivo ya no existe, no falla. */
    void eliminar(String nombre);
}
