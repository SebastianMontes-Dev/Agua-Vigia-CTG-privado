package com.aguavigia.ctg.infrastructure.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador solo para RateLimitConfigTest — no hay controladores reales en esta rama. */
@RestController
public class ControladorDePruebaRateLimit {

    @GetMapping("/api/sectores/prueba-rate-limit/protegida")
    public String protegida() {
        return "ok";
    }

    @GetMapping("/ruta-no-declarada")
    public String noDeclarada() {
        return "ok";
    }

    @GetMapping("/api/sectores/prueba-rate-limit/sin-proteger")
    public String sinProteger() {
        return "ok";
    }
}
