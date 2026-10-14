package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.FormatoNoPermitidoException;
import com.aguavigia.ctg.domain.FotoGuardada;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SubidaNoAutorizadaException;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.out.AlmacenamientoPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SubidaDeFotoRepository;
import com.aguavigia.ctg.domain.port.out.TokenDeSubidaPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * La subida de una foto es pública y sin cuenta, así que lo que la autoriza es el token que solo recibió el autor del
 * reporte: de un solo uso y atado a ese reporte. Sin él, conocer el id de un reporte ajeno no sirve de nada.
 */
class AgregarEvidenciaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T15:30:00Z");
    private static final ReporteId ID = new ReporteId("r1");
    private static final String TOKEN = "token-en-claro";
    private static final String HASH_DEL_TOKEN = "hash-del-token";

    private ReporteCiudadanoRepository reportes;
    private AlmacenamientoPort almacenamiento;
    private SubidaDeFotoRepository subidas;
    private AgregarEvidenciaService servicio;

    @BeforeEach
    void montar() {
        reportes = mock(ReporteCiudadanoRepository.class);
        almacenamiento = mock(AlmacenamientoPort.class);
        subidas = mock(SubidaDeFotoRepository.class);
        TokenDeSubidaPort tokens = mock(TokenDeSubidaPort.class);
        given(tokens.hash(TOKEN)).willReturn(HASH_DEL_TOKEN);
        servicio = new AgregarEvidenciaService(reportes, almacenamiento, subidas, tokens, () -> AHORA);

        given(reportes.asignarFotoSiNoTiene(any(), any(), any())).willReturn(true);
        given(reportes.buscarPorId(ID)).willReturn(Optional.of(reporte()));
        given(subidas.consumir(ID, HASH_DEL_TOKEN, AHORA)).willReturn(true);
    }

    private static ReporteCiudadano reporte() {
        return new ReporteCiudadano(ID, new SectorId("manga"), TipoReporte.SIN_AGUA,
                null, new HuellaDispositivo("hash-1"), AHORA);
    }

    // Firma real de JPEG (FF D8 FF): se verifica el contenido y no solo el Content-Type declarado.
    private static final byte[] JPEG_DE_PRUEBA = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @Test
    void debeGuardarLaFotoConLaExtensionDerivadaDelContentTypeYGuardarSuUrlYSuHash() {
        given(almacenamiento.guardar(eq(".jpg"), any())).willReturn(new FotoGuardada("/api/fotos/uuid.jpg", "a".repeat(64)));

        given(reportes.buscarPorId(ID)).willReturn(Optional.of(reporte()),
                Optional.of(reporte().conFoto("/api/fotos/uuid.jpg", "a".repeat(64))));

        ReporteCiudadano actualizado = servicio.agregarEvidencia("r1", TOKEN, "image/jpeg", JPEG_DE_PRUEBA);

        assertThat(actualizado.fotoUrl()).isEqualTo("/api/fotos/uuid.jpg");
        assertThat(actualizado.fotoSha256()).isEqualTo("a".repeat(64));
        verify(almacenamiento).guardar(".jpg", JPEG_DE_PRUEBA);
        // Solo la foto, atómicamente: nada de guardar el documento entero.
        verify(reportes).asignarFotoSiNoTiene(ID, "/api/fotos/uuid.jpg", "a".repeat(64));
        verify(reportes, never()).guardar(any());
    }

    @Test
    void debeConsumirElTokenAlSubirLaFoto() {
        given(almacenamiento.guardar(eq(".jpg"), any())).willReturn(new FotoGuardada("/api/fotos/uuid.jpg", "a".repeat(64)));

        servicio.agregarEvidencia("r1", TOKEN, "image/jpeg", JPEG_DE_PRUEBA);

        verify(subidas).consumir(ID, HASH_DEL_TOKEN, AHORA);
    }

    /** Conocer el id de un reporte ajeno (es público en la bitácora) no basta para ponerle una foto. */
    @Test
    void sinTokenNoDebeSubirNada() {
        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", null, "image/jpeg", JPEG_DE_PRUEBA))
                .isInstanceOf(SubidaNoAutorizadaException.class);
        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", "  ", "image/jpeg", JPEG_DE_PRUEBA))
                .isInstanceOf(SubidaNoAutorizadaException.class);

        verify(almacenamiento, never()).guardar(any(), any());
        verify(reportes, never()).guardar(any());
    }

    @Test
    void conUnTokenQueNoEsDeEseReporteONoVigenteNoDebeSubirNada() {
        given(subidas.consumir(ID, HASH_DEL_TOKEN, AHORA)).willReturn(false);

        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, "image/jpeg", JPEG_DE_PRUEBA))
                .isInstanceOf(SubidaNoAutorizadaException.class);

        verify(almacenamiento, never()).guardar(any(), any());
        verify(reportes, never()).guardar(any());
    }

    /** El mismo error para un reporte que no existe y para uno con otro token: no se revela cuáles ids existen. */
    @Test
    void unReporteInexistenteDebeResponderComoUnTokenInvalido() {
        given(subidas.consumir(eq(new ReporteId("no-existe")), any(), any())).willReturn(false);

        assertThatThrownBy(() -> servicio.agregarEvidencia("no-existe", TOKEN, "image/jpeg", JPEG_DE_PRUEBA))
                .isInstanceOf(SubidaNoAutorizadaException.class);
    }

    /** Un archivo que se rechaza no gasta el token: el autor puede reintentar con otra foto. */
    @Test
    void unArchivoRechazadoNoDebeGastarElToken() {
        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, "image/jpeg", "no soy una imagen".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(subidas, never()).consumir(any(), any(), any());
        verify(almacenamiento, never()).guardar(any(), any());
    }

    @Test
    void debeRechazarUnArchivoCuyoContenidoNoCoincideConElContentTypeDeclarado() {
        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, "image/png", JPEG_DE_PRUEBA))
                .isInstanceOf(IllegalArgumentException.class);

        verify(almacenamiento, never()).guardar(any(), any());
        verify(reportes, never()).guardar(any());
    }

    /**
     * Un WebP auténtico tampoco pasa: este backend no tiene decodificador, así que no puede recomprimirlo ni quitarle el
     * EXIF (ubicación y cámara del teléfono). Responde 415 para que el cliente sepa que es el formato.
     */
    @Test
    void debeRechazarUnWebpConFormatoNoPermitido() {
        byte[] webpReal = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, "image/webp", webpReal))
                .isInstanceOf(FormatoNoPermitidoException.class)
                .hasMessageContaining("image/webp");

        verify(subidas, never()).consumir(any(), any(), any());
        verify(almacenamiento, never()).guardar(any(), any());
    }

    @Test
    void debeRechazarUnTipoDeArchivoNoPermitido() {
        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, "image/svg+xml", new byte[]{1}))
                .isInstanceOf(FormatoNoPermitidoException.class);

        verify(almacenamiento, never()).guardar(any(), any());
    }

    @Test
    void debeRechazarUnContentTypeNulo() {
        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, null, new byte[]{1}))
                .isInstanceOf(FormatoNoPermitidoException.class);
    }

    /** Una vez puesta, la evidencia no se reemplaza. */
    @Test
    void noDebeReemplazarLaFotoDeUnReporteQueYaTieneEvidencia() {
        given(reportes.buscarPorId(ID)).willReturn(
                Optional.of(reporte().conFoto("/api/fotos/original.jpg", "b".repeat(64))));

        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, "image/jpeg", JPEG_DE_PRUEBA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya tiene una foto");

        verify(almacenamiento, never()).guardar(any(), any());
    }

    /** Una imagen con firma válida y cuerpo corrupto falla al recomprimirse, ya con el token gastado: el autor debe poder reintentar. */
    @Test
    void siElProcesadoDeLaImagenFallaElTokenSeDevuelveParaReintentar() {
        given(almacenamiento.guardar(eq(".jpg"), any())).willThrow(new IllegalArgumentException("imagen corrupta"));

        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, "image/jpeg", JPEG_DE_PRUEBA))
                .isInstanceOf(IllegalArgumentException.class);

        // Vuelve a quedar vivo un token con el mismo hash, por un plazo corto para reintentar.
        verify(subidas).guardar(eq(ID), eq(HASH_DEL_TOKEN), org.mockito.ArgumentMatchers.argThat(vence ->
                vence.isAfter(AHORA) && vence.isBefore(AHORA.plusSeconds(600))));
    }

    @Test
    void siElReporteYaTieneFotoElTokenNoSeDevuelve() {
        given(almacenamiento.guardar(eq(".jpg"), any())).willReturn(new FotoGuardada("/api/fotos/uuid.jpg", "a".repeat(64)));
        given(reportes.asignarFotoSiNoTiene(any(), any(), any())).willReturn(false);

        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, "image/jpeg", JPEG_DE_PRUEBA))
                .isInstanceOf(IllegalStateException.class);

        verify(subidas, never()).guardar(any(), any(), any());
    }

    @Test
    void siElTokenEraValidoPeroElReporteYaNoExisteDebeSer404() {
        given(reportes.buscarPorId(ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.agregarEvidencia("r1", TOKEN, "image/jpeg", JPEG_DE_PRUEBA))
                .isInstanceOf(EntidadNoEncontradaException.class);
    }
}
