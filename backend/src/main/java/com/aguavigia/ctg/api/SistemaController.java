package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.ModoDelSistemaRespuesta;
import com.aguavigia.ctg.domain.ModoDelSistema;
import com.aguavigia.ctg.domain.port.in.ConsultarModoDelSistemaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sistema", description = "Qué instancia es y cuánto de lo que contiene es sintético")
@RestController
@RequestMapping(value = "/api/sistema", produces = MediaType.APPLICATION_JSON_VALUE)
public class SistemaController {

    private final ConsultarModoDelSistemaUseCase consultar;

    public SistemaController(ConsultarModoDelSistemaUseCase consultar) {
        this.consultar = consultar;
    }

    @Operation(summary = "Modo del sistema",
            description = """
                    `modo` es REAL o SIMULACION: la interfaz muestra un banner permanente en la simulacion, que nunca se
                    presenta como real. `cuentasSinteticas` es cuantas cuentas de vecino las creo el sistema para probar el
                    volumen: se dicen tal cual, no son adopcion. Se recuerda un minuto.""")
    @GetMapping("/modo")
    public ModoDelSistemaRespuesta modo() {
        ModoDelSistema modo = consultar.consultar();
        return new ModoDelSistemaRespuesta(modo.modo().name(), modo.cuentasSinteticas());
    }
}
