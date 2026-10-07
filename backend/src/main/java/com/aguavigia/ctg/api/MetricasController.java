package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.MetricasDelSistemaRespuesta;
import com.aguavigia.ctg.domain.port.in.ConsultarMetricasDelSistemaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Observabilidad mínima (D37) para el panel. Protegido por `SecurityConfig` (`/api/veedor/**` exige token) y por el permiso VER_PANEL; `actuator`
 * sigue mostrando solo `health`.
 */
@Tag(name = "Veedor - Sistema", description = "Contadores para calibrar umbrales y plazos")
@RestController
@RequestMapping(value = "/api/veedor/sistema", produces = MediaType.APPLICATION_JSON_VALUE)
public class MetricasController {

    private final ConsultarMetricasDelSistemaUseCase metricas;

    public MetricasController(ConsultarMetricasDelSistemaUseCase metricas) {
        this.metricas = metricas;
    }

    @Operation(operationId = "consultarMetricasDelSistema", summary = "Contadores de calibración del sistema",
            description = """
                    Cambios de estado, disputas, quórums rechazados por composición, reportes por nivel de verificación, fallos de colectores y
                    el tiempo entre el primer reporte y el cambio de estado. Son de este proceso desde `desde`: un reinicio los pone a cero. Sirven
                    para calibrar los umbrales y los plazos, que son valores iniciales sin datos reales que los respalden.""")
    @PreAuthorize("hasAuthority('PERM_VER_PANEL')")
    @GetMapping("/metricas")
    public MetricasDelSistemaRespuesta metricas() {
        return MetricasDelSistemaRespuesta.de(metricas.consultar());
    }
}
