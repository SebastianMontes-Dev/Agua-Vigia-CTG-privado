package com.aguavigia.ctg.api;

import com.aguavigia.ctg.domain.FotoLeida;
import com.aguavigia.ctg.domain.port.in.ObtenerFotoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Las fotos de los reportes (D10). Antes se servía todo el directorio de fotos sin mirar la moderación; ahora sale
 * por aquí, y quien decide si una foto se ve es el reporte al que pertenece.
 */
@Tag(name = "Fotos", description = "Fotos de los reportes: públicas solo si el reporte está aprobado")
@RestController
public class FotoController {

    private final ObtenerFotoUseCase obtenerFoto;

    public FotoController(ObtenerFotoUseCase obtenerFoto) {
        this.obtenerFoto = obtenerFoto;
    }

    @Operation(summary = "Ver la foto de un reporte aprobado",
            description = """
                    Sin sesion. Responde 404 si la foto no existe, si su reporte aun no esta aprobado o si el veedor la
                    descarto: son el mismo 404 a proposito, para que nadie pueda sondear que fotos hay. La interfaz
                    debe mirar `fotoEstado` del reporte y no pedir la imagen mientras este EN_REVISION.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La imagen (image/jpeg o image/png)",
                    content = @Content(mediaType = "image/jpeg")),
            @ApiResponse(responseCode = "404", description = "No hay una foto publica con ese nombre")
    })
    @GetMapping("/api/fotos/{nombre}")
    public ResponseEntity<byte[]> foto(@PathVariable("nombre") String nombre) {
        // Un minuto y no más: si el veedor descarta la foto, no debe seguir sirviéndose desde cachés compartidas.
        return responder(obtenerFoto.paraPublico(nombre).orElse(null),
                CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic());
    }

    @Operation(summary = "Ver la foto de cualquier reporte (panel)",
            description = "Para moderar: el panel ve la foto en cualquier estado, aprobada o no.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La imagen (image/jpeg o image/png)",
                    content = @Content(mediaType = "image/jpeg")),
            @ApiResponse(responseCode = "404", description = "Ningun reporte reclama esa foto")
    })
    @PreAuthorize("hasAuthority('PERM_VER_PANEL')")
    @GetMapping("/api/veedor/fotos/{nombre}")
    public ResponseEntity<byte[]> fotoDelPanel(@PathVariable("nombre") String nombre) {
        return responder(obtenerFoto.paraPanel(nombre).orElse(null), CacheControl.noStore());
    }

    private static ResponseEntity<byte[]> responder(FotoLeida foto, CacheControl cache) {
        if (foto == null) {
            return ResponseEntity.notFound().build();
        }
        // nosniff: el tipo sale de la extensión que guardó el servidor, pero un navegador que adivine el contenido
        // podría tratar como HTML un archivo mal etiquetado.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.tipo()))
                .cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff")
                .body(foto.contenido());
    }
}
