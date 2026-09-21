package com.aguavigia.ctg.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Publica el contrato que consume el frontend. El archivo versionado backend/openapi.yaml se genera
 * desde aqui — no se escribe a mano, para que no pueda desviarse de lo que el backend responde de
 * verdad: `./mvnw test -Dtest=ContratoOpenApiTest -Dopenapi.regenerar=true`.
 */
@Configuration
public class OpenApiConfig {

    static final String ESQUEMA_BEARER = "bearerAuth";
    private static final String PREFIJO_PANEL = "/api/veedor/";
    private static final String RUTA_LOGIN = "/api/veedor/sesion";

    @Bean
    public OpenAPI apiDeAguaVigia() {
        return new OpenAPI()
                // Servidor relativo y no http://localhost:8080: el archivo se versiona, y la URL
                // que springdoc deduce sola es la de la maquina que lo genero.
                .servers(List.of(new Server().url("/").description("Mismo origen que sirve la API")))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("""
                                Token que devuelve POST /api/veedor/sesion, en la cabecera
                                `Authorization: Bearer <token>`. Caduca a las 8 horas y no se renueva: al
                                caducar hay que volver a iniciar sesión.""")))
                .info(new Info()
                .title("AguaVigia CTG — API publica")
                .version("1.0.0")
                .description("""
                        Monitoreo ciudadano del acueducto de Cartagena de Indias.

                        Plataforma ciudadana e independiente: no esta afiliada a Aguas de Cartagena
                        S.A. E.S.P. ni a ninguna entidad distrital.""")
                .contact(new Contact().name("AguaVigia CTG"))
                .license(new License().name("Proyecto de aula")));
    }

    /**
     * Todo lo de `/api/veedor/**` exige sesión salvo el login. Se marca aquí, por ruta, y no operación
     * por operación con anotaciones: SecurityConfig ya decide lo mismo por ruta (una sola regla, un
     * solo sitio), y así una ruta nueva del panel nace documentada como protegida sin acordarse.
     */
    @Bean
    public OpenApiCustomizer seguridadDelPanel() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((ruta, item) -> {
                if (!ruta.startsWith(PREFIJO_PANEL)) {
                    return;
                }
                item.readOperationsMap().forEach((metodo, operacion) -> {
                    boolean esLogin = ruta.equals(RUTA_LOGIN) && metodo.name().equals("POST");
                    if (esLogin) {
                        return;
                    }
                    operacion.addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
                    agregarSiFalta(operacion, "401", "Sin sesión, token inválido, caducado o revocado");
                    agregarSiFalta(operacion, "403", "La sesión no tiene el permiso que exige esta operación");
                });
            });
        };
    }

    private static void agregarSiFalta(Operation operacion, String codigo, String descripcion) {
        if (operacion.getResponses().containsKey(codigo)) {
            return;
        }
        operacion.getResponses().addApiResponse(codigo, new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")))));
    }
}
