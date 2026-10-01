package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.SectorRespuesta;
import com.aguavigia.ctg.api.mapper.SectorApiMapper;
import com.aguavigia.ctg.domain.port.in.ListarDisputasUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * La cola de disputas del veedor: los barrios que un quórum de vecinos contradice sin que cambie su color.
 * Protegido por `SecurityConfig` (`/api/veedor/**` exige token); leer no tiene regla de negocio propia más allá
 * de qué barrios cuentan como disputa, que vive en el caso de uso.
 */
@Tag(name = "Veedor - Disputas", description = "Barrios cuyo estado oficial contradicen los vecinos")
@RestController
@RequestMapping(value = "/api/veedor/disputas", produces = MediaType.APPLICATION_JSON_VALUE)
public class DisputaController {

    private final ListarDisputasUseCase disputas;
    private final SectorApiMapper mapper;

    public DisputaController(ListarDisputasUseCase disputas, SectorApiMapper mapper) {
        this.disputas = disputas;
        this.mapper = mapper;
    }

    @Operation(operationId = "listarDisputas", summary = "Listar los barrios en disputa, los más contradichos primero",
            description = "Cada barrio trae `reportesEnContra`: cuántos vecinos sostienen que el estado oficial no es el real.")
    @PreAuthorize("hasAuthority('PERM_VER_PANEL')")
    @GetMapping
    public List<SectorRespuesta> listar() {
        return mapper.aRespuestas(disputas.listar());
    }
}
