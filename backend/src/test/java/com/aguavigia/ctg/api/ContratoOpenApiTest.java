package com.aguavigia.ctg.api;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `backend/openapi.yaml` es el contrato versionado del que el frontend genera su cliente.
 * Se genera con la aplicación corriendo y se comitea a mano, así que nada garantizaba que siguiera
 * al día — un endpoint nuevo sin regenerar y el frontend queda programando contra un contrato que
 * ya no existe.
 *
 * Se comparan las **rutas**, no el YAML entero: un cambio de redacción en una descripción no
 * debería romper la build, pero un endpoint que aparece o desaparece sí.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // MailSenderAutoConfiguration solo crea el JavaMailSender si hay host; aquí no se envía
        // ningún correo, solo hace falta que el contexto pueda construir MailNotificacionAdapter.
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        // El ciclo de ingesta no debe salir a la red durante esta prueba. COLLECTOR_USER_AGENT
        // viaja vacío por defecto, así que los dos colectores se niegan a llamar y
        // PipelineOrquestador los absorbe — el intervalo largo es la segunda línea.
        "aguavigia.ingesta.user-agent=",
        "aguavigia.ingesta.intervalo-ms=86400000",
        "aguavigia.rate-limit.reglas="
})
class ContratoOpenApiTest {

    private static final Path CONTRATO = Path.of("openapi.yaml");
    private static final Pattern RUTA_EN_JSON = Pattern.compile("\"(/api/[^\"]*)\"\\s*:\\s*\\{");
    private static final Pattern RUTA_EN_YAML = Pattern.compile("(?m)^  (/api/\\S*):\\s*$");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    /**
     * Regenera `backend/openapi.yaml` desde el código, sin levantar la aplicación:
     *   ./mvnw test -Dtest=ContratoOpenApiTest -Dopenapi.regenerar=true
     * Después revisar el diff y comitear. No es un test: si no se pide, no hace nada.
     */
    @Test
    void regenerarElContratoSoloCuandoSePide() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("openapi.regenerar") != null,
                "Se regenera solo con -Dopenapi.regenerar=true");

        String yaml = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(CONTRATO, yaml, java.nio.charset.StandardCharsets.UTF_8);

        // Copia en JSON, sin versionar (target/): la lee scripts/generar-referencia-api.mjs, que no
        // trae un parser de YAML.
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "openapi.json"), json, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void elContratoVersionadoDebeCoincidirConLoQueElBackendExponeDeVerdad() throws Exception {
        String contratoVivo = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Set<String> rutasReales = extraer(RUTA_EN_JSON, contratoVivo);
        Set<String> rutasVersionadas = extraer(RUTA_EN_YAML, Files.readString(CONTRATO));

        assertThat(rutasReales)
                .as("""
                        backend/openapi.yaml quedó desincronizado con el código.
                        Regeneralo (no hace falta levantar la aplicación):
                          cd backend && ./mvnw test -Dtest=ContratoOpenApiTest -Dopenapi.regenerar=true
                        """)
                .isEqualTo(rutasVersionadas);
    }

    /**
     * La prueba de arriba solo compara qué rutas existen. Un endpoint que sigue ahí pero cambió de
     * método, perdió un parámetro requerido, dejó de exigir el cuerpo, cambió sus códigos de
     * respuesta o su esquema de seguridad rompería a cualquier cliente generado del contrato sin que
     * esa prueba se enterara. Se reutiliza el propio modelo de swagger-core que ya trae
     * springdoc-openapi (`Json`/`Yaml` de `io.swagger.v3.core.util`, `OpenAPI` de
     * `io.swagger.v3.oas.models`) — sin agregar ninguna dependencia nueva.
     */
    @Test
    void elContratoVersionadoDebeCoincidirSemanticamenteConLoQueElBackendExponeDeVerdad() throws Exception {
        String contratoVivo = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OpenAPI vivo = Json.mapper().readValue(contratoVivo, OpenAPI.class);
        OpenAPI versionado = Yaml.mapper().readValue(Files.readString(CONTRATO), OpenAPI.class);

        assertThat(firmasDeOperaciones(vivo))
                .as("""
                        backend/openapi.yaml quedó desincronizado con el código en algo más que el
                        conjunto de rutas: un método, un parámetro requerido, un código de respuesta o
                        un esquema de seguridad. Regeneralo:
                          cd backend && ./mvnw test -Dtest=ContratoOpenApiTest -Dopenapi.regenerar=true
                        """)
                .isEqualTo(firmasDeOperaciones(versionado));
    }

    /**
     * Una firma por operación (método + ruta): parámetros requeridos, si el cuerpo es obligatorio,
     * los códigos de respuesta declarados y los esquemas de seguridad exigidos. No compara
     * descripciones ni ejemplos — esos sí pueden cambiar sin romper a ningún cliente.
     */
    private static Map<String, String> firmasDeOperaciones(OpenAPI openapi) {
        Map<String, String> firmas = new LinkedHashMap<>();
        if (openapi.getPaths() == null) {
            return firmas;
        }
        openapi.getPaths().forEach((ruta, item) -> operacionesDe(item).forEach((metodo, operacion) -> {
            List<String> parametrosRequeridos = new ArrayList<>();
            if (operacion.getParameters() != null) {
                for (Parameter parametro : operacion.getParameters()) {
                    if (Boolean.TRUE.equals(parametro.getRequired())) {
                        parametrosRequeridos.add(parametro.getIn() + ":" + parametro.getName());
                    }
                }
            }
            parametrosRequeridos.sort(String::compareTo);

            boolean cuerpoRequerido = operacion.getRequestBody() != null
                    && Boolean.TRUE.equals(operacion.getRequestBody().getRequired());

            Set<String> codigosDeRespuesta = new TreeSet<>();
            if (operacion.getResponses() != null) {
                codigosDeRespuesta.addAll(operacion.getResponses().keySet());
            }

            Set<String> esquemasDeSeguridad = new TreeSet<>();
            if (operacion.getSecurity() != null) {
                for (SecurityRequirement requisito : operacion.getSecurity()) {
                    esquemasDeSeguridad.addAll(requisito.keySet());
                }
            }

            firmas.put(metodo + " " + ruta, "parametrosRequeridos=" + parametrosRequeridos
                    + " cuerpoRequerido=" + cuerpoRequerido
                    + " respuestas=" + codigosDeRespuesta
                    + " seguridad=" + esquemasDeSeguridad);
        }));
        return firmas;
    }

    private static Map<String, Operation> operacionesDe(PathItem item) {
        Map<String, Operation> operaciones = new LinkedHashMap<>();
        if (item.getGet() != null) operaciones.put("GET", item.getGet());
        if (item.getPost() != null) operaciones.put("POST", item.getPost());
        if (item.getPut() != null) operaciones.put("PUT", item.getPut());
        if (item.getPatch() != null) operaciones.put("PATCH", item.getPatch());
        if (item.getDelete() != null) operaciones.put("DELETE", item.getDelete());
        return operaciones;
    }

    /**
     * Sin esto, un cliente generado desde el contrato no sabe que el panel exige sesión: ningún
     * endpoint de `/api/veedor/**` figuraba como protegido y el 401/403 no estaba documentado.
     */
    @Test
    void elContratoDebeDeclararQueElPanelExigeElTokenBearer() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.paths['/api/veedor/cortes'].post.security[0].bearerAuth").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.paths['/api/veedor/cortes'].post.responses['401']").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.paths['/api/veedor/cortes'].post.responses['403']").exists());
    }

    /** Ni el login ni las rutas públicas piden token: marcarlas como protegidas confundiría al cliente. */
    @Test
    void elLoginYLasRutasPublicasNoDebenExigirToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.paths['/api/veedor/sesion'].post.security").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.paths['/api/sectores'].get.security").doesNotExist());
    }

    @Test
    void elContratoDebePublicarLasRutasQueConsumeElFrontend() throws Exception {
        String contratoVivo = mockMvc.perform(get("/v3/api-docs")).andReturn()
                .getResponse().getContentAsString();

        assertThat(extraer(RUTA_EN_JSON, contratoVivo)).contains(
                "/api/sectores", "/api/reportes", "/api/suscripciones", "/api/bitacora",
                "/api/estadisticas", "/api/cumplimiento");
    }

    private static Set<String> extraer(Pattern patron, String texto) {
        Set<String> rutas = new TreeSet<>();
        Matcher coincidencia = patron.matcher(texto);
        while (coincidencia.find()) {
            rutas.add(coincidencia.group(1));
        }
        return rutas;
    }

    /** Sanity check del propio test: si los patrones dejaran de encontrar rutas, compararía dos
     * conjuntos vacíos y pasaría siempre. */
    @Test
    void elPropioTestDebeEncontrarRutasEnAmbosFormatos() throws Exception {
        String contratoVivo = mockMvc.perform(get("/v3/api-docs")).andReturn()
                .getResponse().getContentAsString();

        assertThat(extraer(RUTA_EN_JSON, contratoVivo)).hasSizeGreaterThan(10);
        assertThat(extraer(RUTA_EN_YAML, Files.readString(CONTRATO))).hasSizeGreaterThan(10);
    }

    /** Documentación viva: si alguien agrega un controlador, esta lista se lo recuerda. */
    @Test
    void todoControladorPublicoDebeEstarEnElContrato() throws Exception {
        String contratoVivo = mockMvc.perform(get("/v3/api-docs")).andReturn()
                .getResponse().getContentAsString();
        Set<String> rutas = extraer(RUTA_EN_JSON, contratoVivo);

        List.of("/api/veedor/ingesta/propuestas", "/api/veedor/ingesta/salud",
                        "/api/cumplimiento/serie", "/api/estadisticas/exportar.csv", "/api/v2/requests.json")
                .forEach(ruta -> assertThat(rutas)
                        .as("La ruta %s no aparece en el contrato", ruta)
                        .anyMatch(publicada -> publicada.startsWith(ruta)));
    }
}
