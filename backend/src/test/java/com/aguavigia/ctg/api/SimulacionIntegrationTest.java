package com.aguavigia.ctg.api;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La instancia de simulación arrancada de verdad: el reloj simulado sustituye al del sistema, el colector simulado a Acuacar, y un boletín
 * que entra por {@code POST /api/sim/boletines} llega al mapa por la ingesta real, y el reloj acelerado hace que el barrido de ventanas
 * lo pase de «corte programado» a «sin servicio» sin esperar horas.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "aguavigia.sim.habilitada=true",
        "aguavigia.sim.clave=clave-de-simulacion-de-prueba-0123",
        "aguavigia.ingesta.modo=simulacion",
        "aguavigia.sistema.modo=SIMULACION",
        "aguavigia.ingesta.ventanas-intervalo-ms=300",
        "aguavigia.rate-limit.reglas="
})
class SimulacionIntegrationTest {

    private static final String CLAVE = "clave-de-simulacion-de-prueba-0123";

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Autowired
    private TestRestTemplate cliente;

    @Autowired
    private SectorRepository sectores;

    private HttpEntity<Object> conClave(Object cuerpo) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set("X-Sim-Key", CLAVE);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(cuerpo, cabeceras);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String ruta, Object cuerpo) {
        ResponseEntity<Map> respuesta = cliente.exchange(ruta, HttpMethod.POST, conClave(cuerpo), Map.class);
        assertThat(respuesta.getStatusCode().is2xxSuccessful()).as("POST %s -> %s %s", ruta, respuesta.getStatusCode(), respuesta.getBody()).isTrue();
        return respuesta.getBody();
    }

    @SuppressWarnings("unchecked")
    private String estadoDe(String sectorId) {
        Map<String, Object> sector = cliente.getForObject("/api/sectores/" + sectorId, Map.class);
        return sector == null ? null : (String) sector.get("estado");
    }

    private static void esperarA(String descripcion, Supplier<Boolean> condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            if (condicion.get()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No ocurrió a tiempo: " + descripcion);
    }

    @Test
    void sinLaClaveLasRutasDeSimulacionNoAtienden() {
        assertThat(cliente.getForEntity("/api/sim/reloj", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void elModoDelSistemaDeclaraLaSimulacion() {
        assertThat(cliente.getForObject("/api/sistema/modo", Map.class)).containsEntry("modo", "SIMULACION");
    }

    @Test
    void unBoletinSimuladoLlegaAlMapaYElRelojAceleradoLoPasaASinServicio() throws Exception {
        sectores.guardar(new Sector(new SectorId("manga"), "Manga", 10754, EstadoServicio.CON_SERVICIO));
        // Día D a las 08:00 de Cartagena (13:00 UTC).
        post("/api/sim/reloj", Map.of("instante", "2026-11-02T13:00:00Z"));

        Map<String, Object> boletin = post("/api/sim/boletines", Map.of(
                "titulo", "[SIMULACIÓN] AGUAS DE CARTAGENA SUSPENDERÁ EL SERVICIO DE ACUEDUCTO EN VARIOS BARRIOS",
                "contenido", "<strong>Cartagena de Indias, 2 de noviembre de 2026.</strong> Aguas de Cartagena informa a la comunidad que este "
                        + "lunes 2 de noviembre, entre las 10:00 a. m. y las 4:00 p. m., ejecutará trabajos en la red de acueducto."
                        + "<br><br>Durante la ejecución de estos trabajos se presentará suspensión del servicio de acueducto en los "
                        + "siguientes barrios y sectores:<br><br>Manga."));
        assertThat(boletin).containsKey("fecha");

        // Antes de las 10:00: anunciado, todavía hay agua.
        esperarA("el boletín deja el barrio en corte programado", () -> "CORTE_PROGRAMADO".equals(estadoDe("manga")));

        // Dos horas simuladas después empieza la ventana y el barrido la pasa a sin servicio.
        post("/api/sim/reloj", Map.of("avanzarSegundos", 7200));
        esperarA("el barrido pasa el barrio a sin servicio", () -> "SIN_SERVICIO".equals(estadoDe("manga")));
    }
}
