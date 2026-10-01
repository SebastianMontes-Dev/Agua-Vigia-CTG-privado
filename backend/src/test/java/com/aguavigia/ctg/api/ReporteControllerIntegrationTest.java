package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.ReporteRespuesta;
import com.aguavigia.ctg.api.dto.SolicitudReporte;
import com.aguavigia.ctg.api.dto.TokenDeDispositivoRespuesta;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración contra el contexto real de Spring (no @WebMvcTest con el use case
 * mockeado): esta es la que hubiera detectado el 500 de POST /api/reportes/{id}/confirmar
 * causado por @Transactional sin TransactionManager para Mongo en el classpath.
 *
 * Desde D7 recorre también la identidad de punta a punta: el dispositivo se pide al servidor, su token
 * viaja en `X-Dispositivo` y sin él no se reporta ni se confirma.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReporteControllerIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate cliente;

    @Autowired
    private SectorRepository sectores;

    @BeforeEach
    void sembrarSector() {
        sectores.guardar(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO));
    }

    private String pedirDispositivo() {
        ResponseEntity<TokenDeDispositivoRespuesta> respuesta =
                cliente.postForEntity("/api/dispositivos", null, TokenDeDispositivoRespuesta.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return respuesta.getBody().token();
    }

    private static HttpEntity<?> con(String token, Object cuerpo) {
        HttpHeaders cabeceras = new HttpHeaders();
        if (token != null) {
            cabeceras.set("X-Dispositivo", token);
        }
        return new HttpEntity<>(cuerpo, cabeceras);
    }

    private ResponseEntity<ReporteRespuesta> reportar(String token) {
        return cliente.exchange("/api/reportes", HttpMethod.POST,
                con(token, new SolicitudReporte("manga", "SIN_AGUA", null, null)), ReporteRespuesta.class);
    }

    @Test
    void debeConfirmarUnReporteExistente() {
        ResponseEntity<ReporteRespuesta> creado = reportar(pedirDispositivo());
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creado.getBody().verificacion()).isEqualTo("NINGUNA");
        String reporteId = creado.getBody().id();

        ResponseEntity<ReporteRespuesta> confirmado = cliente.exchange(
                "/api/reportes/" + reporteId + "/confirmar", HttpMethod.POST,
                con(pedirDispositivo(), null), ReporteRespuesta.class);

        assertThat(confirmado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmado.getBody().confirmaciones()).isEqualTo(1);
    }

    @Test
    void sinTokenDeDispositivoNoSeDebeReportar() {
        assertThat(reportar(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Un token que no firmó este servidor no es una identidad: ya no basta inventar una cadena larga. */
    @Test
    void unTokenInventadoNoDebeServirParaReportar() {
        assertThat(reportar("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0.firma-inventada").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unTokenConElIdCambiadoNoDebeServirParaReportar() {
        String token = pedirDispositivo();
        String firma = token.substring(token.indexOf('.') + 1);

        assertThat(reportar("otro-id." + firma).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** El cupo es por la identidad que firma el servidor: pedir otro token es lo único que lo reinicia, y cuesta una petición limitada. */
    @Test
    void elCupoDeReportesDebeSerPorDispositivo() {
        String token = pedirDispositivo();

        for (int i = 0; i < 3; i++) {
            assertThat(reportar(token).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        assertThat(reportar(token).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(reportar(pedirDispositivo()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void confirmarSinTokenNoDebePoderse() {
        String reporteId = reportar(pedirDispositivo()).getBody().id();

        ResponseEntity<String> respuesta = cliente.exchange(
                "/api/reportes/" + reporteId + "/confirmar", HttpMethod.POST, con(null, null), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
