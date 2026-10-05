package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.ReporteRespuesta;
import com.aguavigia.ctg.api.dto.SolicitudReporte;
import com.aguavigia.ctg.api.dto.TokenDeDispositivoRespuesta;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.DescartarFotoUseCase;
import com.aguavigia.ctg.domain.port.in.ModerarReporteUseCase;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La foto de punta a punta contra el contexto real (D10): el token de subida que solo recibe el autor, la foto que
 * nadie ve hasta que el reporte se aprueba, y la que el veedor retira.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FotoDeReporteIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @TempDir
    static Path carpetaDeFotos;

    @DynamicPropertySource
    static void carpeta(DynamicPropertyRegistry registro) {
        registro.add("aguavigia.almacenamiento.directorio-fotos", () -> carpetaDeFotos.toString());
    }

    @Autowired
    private TestRestTemplate cliente;

    @Autowired
    private SectorRepository sectores;

    @Autowired
    private ModerarReporteUseCase moderar;

    @Autowired
    private DescartarFotoUseCase descartarFoto;

    @BeforeEach
    void sembrarSector() {
        sectores.guardar(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO));
    }

    private ReporteRespuesta reportar() {
        String dispositivo = cliente.postForEntity("/api/dispositivos", null, TokenDeDispositivoRespuesta.class)
                .getBody().token();
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set("X-Dispositivo", dispositivo);
        ResponseEntity<ReporteRespuesta> creado = cliente.exchange("/api/reportes", HttpMethod.POST,
                new HttpEntity<>(new SolicitudReporte("manga", "SIN_AGUA", null, null), cabeceras),
                ReporteRespuesta.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return creado.getBody();
    }

    private static byte[] jpeg() throws IOException {
        BufferedImage imagen = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", salida);
        return salida.toByteArray();
    }

    private ResponseEntity<ReporteRespuesta> subir(String reporteId, String tokenDeSubida) throws IOException {
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("foto", new ByteArrayResource(jpeg()) {
            @Override
            public String getFilename() {
                return "foto.jpg";
            }
        });
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (tokenDeSubida != null) {
            cabeceras.set("X-Subida", tokenDeSubida);
        }
        return cliente.exchange("/api/reportes/" + reporteId + "/foto", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), ReporteRespuesta.class);
    }

    private ResponseEntity<byte[]> verPublica(String fotoUrl) {
        return cliente.getForEntity(fotoUrl, byte[].class);
    }

    @Test
    void alReportarSoloSuAutorRecibeElTokenDeSubida() {
        ReporteRespuesta creado = reportar();

        assertThat(creado.subidaToken()).isNotBlank();
        assertThat(creado.fotoEstado()).isEqualTo("SIN_FOTO");
    }

    /** Hallazgo 7 del plan: con solo el id del reporte —público en la bitácora— ya no se puede ocupar su foto. */
    @Test
    void sinElTokenDeSubidaNoSePuedeAdjuntarUnaFotoAlReporteDeOtro() throws Exception {
        ReporteRespuesta ajeno = reportar();

        assertThat(subir(ajeno.id(), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(subir(ajeno.id(), "token-adivinado").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void elTokenDeUnReporteNoSirveParaOtro() throws Exception {
        ReporteRespuesta uno = reportar();
        ReporteRespuesta otro = reportar();

        assertThat(subir(otro.id(), uno.subidaToken()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void laFotoEsDeUnSoloUsoYNoSeVeHastaQueElReporteSeApruebe() throws Exception {
        ReporteRespuesta creado = reportar();

        ResponseEntity<ReporteRespuesta> subida = subir(creado.id(), creado.subidaToken());
        assertThat(subida.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(subida.getBody().fotoEstado()).isEqualTo("EN_REVISION");
        String fotoUrl = subida.getBody().fotoUrl();
        assertThat(fotoUrl).startsWith("/api/fotos/");

        // Misma respuesta que si la foto no existiera: nadie ve evidencia que nadie moderó.
        assertThat(verPublica(fotoUrl).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // El token se gastó: repetirlo no sirve.
        assertThat(subir(creado.id(), creado.subidaToken()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        moderar.aprobar(new ReporteId(creado.id()));
        ResponseEntity<byte[]> aprobada = verPublica(fotoUrl);
        assertThat(aprobada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aprobada.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(aprobada.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");

        descartarFoto.descartarFoto(new ReporteId(creado.id()));
        assertThat(verPublica(fotoUrl).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unaFotoWebPSeRechazaConUnTipoPropioYNoGastaElToken() throws Exception {
        ReporteRespuesta creado = reportar();
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        HttpHeaders parte = new HttpHeaders();
        parte.setContentType(MediaType.parseMediaType("image/webp"));
        cuerpo.add("foto", new HttpEntity<>(new ByteArrayResource(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0}) {
            @Override
            public String getFilename() {
                return "foto.webp";
            }
        }, parte));
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.MULTIPART_FORM_DATA);
        cabeceras.set("X-Subida", creado.subidaToken());

        ResponseEntity<String> rechazada = cliente.exchange("/api/reportes/" + creado.id() + "/foto",
                HttpMethod.POST, new HttpEntity<>(cuerpo, cabeceras), String.class);

        assertThat(rechazada.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(rechazada.getBody()).contains("formato-no-permitido");
        // El archivo se rechazó antes de gastar el token: el autor puede reintentar con una foto buena.
        assertThat(subir(creado.id(), creado.subidaToken()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void laRutaVieja_DeFotos_YaNoSeSirve() {
        assertThat(cliente.getForEntity("/fotos/cualquiera.jpg", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
