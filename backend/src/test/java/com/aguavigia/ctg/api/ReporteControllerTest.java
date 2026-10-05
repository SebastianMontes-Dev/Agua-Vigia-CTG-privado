package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.api.mapper.ReporteApiMapperImpl;
import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.DispositivoInvalidoException;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.LimiteReportesExcedidoException;
import com.aguavigia.ctg.domain.NivelDeVerificacion;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.AgregarEvidenciaUseCase;
import com.aguavigia.ctg.domain.port.in.ConfirmarReporteUseCase;
import com.aguavigia.ctg.domain.port.in.IdentificarReportanteUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReporteController.class)
@Import({ReporteApiMapperImpl.class, ManejadorGlobalDeErrores.class, SecurityConfig.class})
// Sin reglas de rate limiting: este slice prueba el contrato del controlador, y con la regla real
// de application.yml el interceptor llamaria al RedisTemplate mockeado. El limitador tiene su
// propia prueba contra un Redis real (RateLimitConfigTest).
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class ReporteControllerTest {

    // SecurityConfig construye JwtAuthenticationFilter con este puerto: el filtro consulta la
    // revocacion en cada peticion con token (ADR-039). Sin el bean, el contexto del slice no carga.
    @MockitoBean
    private RevocacionSesionPort revocacion;

    private static final Instant AHORA = Instant.parse("2026-08-08T15:30:00Z");
    private static final Reportante REPORTANTE = Reportante.anonimo(new HuellaDispositivo("huella-del-servidor"));
    private static final String TOKEN_JWT = "jwt-de-prueba";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrarReporteUseCase registrarReporte;

    @MockitoBean
    private AgregarEvidenciaUseCase agregarEvidenciaUseCase;

    @MockitoBean
    private ConfirmarReporteUseCase confirmarReporte;

    @MockitoBean
    private IdentificarReportanteUseCase identificar;

    @MockitoBean
    private JwtProvider jwtProvider;

    // Igual que en SectorControllerTest/SuscripcionControllerTest: RateLimitConfig exige este bean
    // en cualquier slice de @WebMvcTest aunque no se importe aquí.
    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @BeforeEach
    void montar() {
        given(revocacion.revocadasAntesDe(any())).willReturn(Optional.empty());
        given(identificar.identificar(any(), any())).willReturn(REPORTANTE);
    }

    private static ReporteCiudadano reporte(String id, String sector, TipoReporte tipo, Coordenada coordenada) {
        return new ReporteCiudadano(new ReporteId(id), new SectorId(sector), tipo, coordenada,
                new HuellaDispositivo("hash-" + id), AHORA);
    }

    private void registraDevolviendo(ReporteCiudadano creado) {
        given(registrarReporte.registrar(any(), any(), any(), any(), any(), any(), anyBoolean())).willReturn(creado);
    }

    // --- registrar ---

    @Test
    void debeRegistrarElReporteYResponder201() throws Exception {
        registraDevolviendo(reporte("r1", "bocagrande", TipoReporte.SIN_AGUA, new Coordenada(10.39, -75.48)));

        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "token-del-dispositivo")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA",
                                 "coordenada":{"latitud":10.39,"longitud":-75.48}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.sectorId").value("bocagrande"))
                .andExpect(jsonPath("$.tipo").value("SIN_AGUA"));
    }

    /** La identidad la resuelve el servidor con la cabecera: el controlador no inventa ni lee otra. */
    @Test
    void debeIdentificarAlReportantePorLaCabeceraYPasarloAlCasoDeUso() throws Exception {
        registraDevolviendo(reporte("r1", "bocagrande", TipoReporte.SIN_AGUA, null));

        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "token-del-dispositivo")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA","precisionMetros":25.5,
                                 "coordenada":{"latitud":10.39,"longitud":-75.48}}"""))
                .andExpect(status().isCreated());

        verify(identificar).identificar(eq("token-del-dispositivo"), isNull());
        verify(registrarReporte).registrar(eq(new SectorId("bocagrande")), eq(TipoReporte.SIN_AGUA),
                eq(new Coordenada(10.39, -75.48)), eq(25.5), eq(REPORTANTE), any(), eq(false));
    }

    @Test
    void debeAceptarUnReporteSinCoordenadaNiPrecision() throws Exception {
        registraDevolviendo(reporte("r2", "bocagrande", TipoReporte.PRESION_BAJA, null));

        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"PRESION_BAJA"}"""))
                .andExpect(status().isCreated());

        verify(registrarReporte).registrar(any(), any(), isNull(), isNull(), any(), any(), anyBoolean());
    }

    /** La respuesta dice cuánto respaldó el servidor al reporte: el cliente lo muestra, no lo decide. */
    @Test
    void laRespuestaDebeLlevarElNivelDeVerificacion() throws Exception {
        registraDevolviendo(reporte("r1", "bocagrande", TipoReporte.SIN_AGUA, null)
                .conIdentidad(NivelDeVerificacion.UBICACION_VERIFICADA, "red"));

        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA"}"""))
                .andExpect(jsonPath("$.verificacion").value("UBICACION_VERIFICADA"))
                .andExpect(jsonPath("$.redHash").doesNotExist());
    }

    /** D7: una `huella` en el cuerpo ya no sirve para nada, ni siquiera a un cliente viejo que la siga mandando. */
    @Test
    void unaHuellaEnElCuerpoNoDebeSerLaIdentidadDelReporte() throws Exception {
        registraDevolviendo(reporte("r1", "bocagrande", TipoReporte.SIN_AGUA, null));

        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA","huella":"huella-inventada-por-el-cliente-32-car"}"""))
                .andExpect(status().isCreated());

        verify(registrarReporte).registrar(any(), any(), any(), any(), eq(REPORTANTE), any(), anyBoolean());
    }

    /**
     * `POST /api/reportes` es público (RF005): nada de lo que mande un cliente anónimo puede otorgar el cupo de
     * sensor. Solo IotController puede hacerlo, y solo tras validar `X-IoT-Key`.
     */
    @Test
    void nuncaDebeMarcarEsSensor() throws Exception {
        registraDevolviendo(reporte("r3", "bocagrande", TipoReporte.SIN_AGUA, null));

        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA"}"""))
                .andExpect(status().isCreated());

        verify(registrarReporte).registrar(any(), any(), any(), any(), any(), any(), eq(false));
    }

    // --- identidad ---

    @Test
    void sinIdentidadDebeResponder401ConElTipoDispositivoInvalido() throws Exception {
        given(identificar.identificar(any(), any()))
                .willThrow(new DispositivoInvalidoException("Falta la identidad del dispositivo."));

        mockMvc.perform(post("/api/reportes")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://aguavigia.example/errores/dispositivo-invalido"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("identidad")));

        verify(registrarReporte, never()).registrar(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void unVecinoConSesionDebeIdentificarseConSuCuenta() throws Exception {
        given(jwtProvider.validar(TOKEN_JWT)).willReturn(Optional.of(AutenticacionDePrueba.sesionDeVecino()));
        registraDevolviendo(reporte("r1", "bocagrande", TipoReporte.SIN_AGUA, null));

        mockMvc.perform(post("/api/reportes")
                        .header("Authorization", "Bearer " + TOKEN_JWT)
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA"}"""))
                .andExpect(status().isCreated());

        verify(identificar).identificar(isNull(), eq(new UsuarioId(AutenticacionDePrueba.USUARIO_ID)));
    }

    /** Con sesión de vecino y cabecera a la vez, ambos llegan al caso de uso, que decide: la cuenta manda. */
    @Test
    void conSesionDeVecinoYCabeceraDebePasarLasDosAlCasoDeUso() throws Exception {
        given(jwtProvider.validar(TOKEN_JWT)).willReturn(Optional.of(AutenticacionDePrueba.sesionDeVecino()));
        registraDevolviendo(reporte("r1", "bocagrande", TipoReporte.SIN_AGUA, null));

        mockMvc.perform(post("/api/reportes")
                        .header("Authorization", "Bearer " + TOKEN_JWT)
                        .header("X-Dispositivo", "token-del-dispositivo")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA"}"""))
                .andExpect(status().isCreated());

        verify(identificar).identificar(eq("token-del-dispositivo"),
                eq(new UsuarioId(AutenticacionDePrueba.USUARIO_ID)));
    }

    /**
     * Quién cuenta como vecino lo decide el caso de uso, que lee la cuenta: el controlador pasa la de la sesión sea
     * cual sea su rol. Un ADMIN reporta como dispositivo (ver IdentificarReportanteServiceTest).
     */
    @Test
    void unaSesionDelPanelDebePasarSuCuentaYDejarQueElCasoDeUsoDecida() throws Exception {
        given(jwtProvider.validar(TOKEN_JWT)).willReturn(Optional.of(AutenticacionDePrueba.sesionDeAdmin()));
        registraDevolviendo(reporte("r1", "bocagrande", TipoReporte.SIN_AGUA, null));

        mockMvc.perform(post("/api/reportes")
                        .header("Authorization", "Bearer " + TOKEN_JWT)
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA"}"""))
                .andExpect(status().isCreated());

        verify(identificar).identificar(eq("t"), eq(new UsuarioId(AutenticacionDePrueba.USUARIO_ID)));
    }

    // --- validación y errores ---

    @Test
    void debeResponder400ConFormatoRfc7807SiElSectorNoExiste() throws Exception {
        given(registrarReporte.registrar(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willThrow(new IllegalArgumentException("No existe el sector 'no-existe'"));

        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"no-existe","tipo":"SIN_AGUA"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No existe el sector 'no-existe'"));
    }

    @Test
    void debeResponder400SiElTipoNoEsValido() throws Exception {
        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"NO_EXISTE"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void debeResponder429CuandoElDispositivoSuperaElLimite() throws Exception {
        given(registrarReporte.registrar(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willThrow(new LimiteReportesExcedidoException("Ya reportaste 3 veces en 'bocagrande'"));

        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Límite de reportes excedido"));
    }

    @Test
    void unaPrecisionNegativaDebeResponder400() throws Exception {
        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"SIN_AGUA","precisionMetros":-3,
                                 "coordenada":{"latitud":10.39,"longitud":-75.48}}"""))
                .andExpect(status().isBadRequest());
    }

    /** RF007: sin sectorId el cliente puede mandar solo la coordenada y el servidor infiere el barrio. */
    @Test
    void debeAceptarUnReporteSinSectorSiViajaLaCoordenada() throws Exception {
        given(registrarReporte.registrar(isNull(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(reporte("r4", "bocagrande", TipoReporte.SIN_AGUA, new Coordenada(10.39, -75.48)));

        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"tipo":"SIN_AGUA","precisionMetros":20,
                                 "coordenada":{"latitud":10.39,"longitud":-75.48}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sectorId").value("bocagrande"));
    }

    @Test
    void elDetalleDelTipoInvalidoNoDebeFiltrarElNombreDeLaClaseDelDominio() throws Exception {
        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande","tipo":"NO_EXISTE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("com.aguavigia"))))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("SIN_AGUA")));
    }

    @Test
    void debeResponder400SiFaltaElTipo() throws Exception {
        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"sectorId":"bocagrande"}"""))
                .andExpect(status().isBadRequest());
    }

    /** BUG-062 — un verbo que la ruta no expone es error del cliente (405), no del servidor (500). */
    @Test
    void debeResponder405ConLaCabeceraAllowSiSeConsultaLaRutaDeReportesConGet() throws Exception {
        mockMvc.perform(get("/api/reportes"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.title").value("Metodo no permitido"));
    }

    /** BUG-062 — mismo origen: sin manejador, un Content-Type ajeno tambien salia por el 500. */
    @Test
    void debeResponder415SiElCuerpoNoViajaComoJson() throws Exception {
        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("text/plain")
                        .content("sin agua en bocagrande"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"));
    }

    @Test
    void debeResponder400EnFormatoRfc7807SiElJsonEstaMalFormado() throws Exception {
        mockMvc.perform(post("/api/reportes")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("{\"sectorId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Peticion invalida"));
    }

    // --- foto (F3 la ata a un token de subida) ---

    /** M10 — subir evidencia sin adjuntar el archivo respondia 500 en vez de decir que falta. */
    @Test
    void debeResponder400SiElMultipartLlegaSinLaFoto() throws Exception {
        mockMvc.perform(multipart("/api/reportes/r1/foto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Peticion invalida"));
    }

    /**
     * CARACTERIZACIÓN, no comportamiento deseado (hallazgo 7 del plan): hoy basta conocer el id de un
     * reporte —que lista `/api/bitacora/{id}/sustento`, público— para adjuntarle una foto, sin cuenta,
     * sin token de dispositivo ni nada que pruebe que se es su autor. Al llegar el token de subida
     * (F3.1) este test debe invertirse: sin `X-Subida` la respuesta será 403.
     */
    @Test
    void hoyCualquieraConElIdPuedeSubirUnaFotoAlReporteDeOtro() throws Exception {
        ReporteCiudadano ajeno = new ReporteCiudadano(
                new ReporteId("r-ajeno"), new SectorId("bocagrande"), TipoReporte.SIN_AGUA,
                null, new HuellaDispositivo("huella-de-otra-persona"), AHORA)
                .conFoto("/fotos/puesta-por-un-desconocido.jpg");
        given(agregarEvidenciaUseCase.agregarEvidencia(eq("r-ajeno"), any(), any())).willReturn(ajeno);
        org.springframework.mock.web.MockMultipartFile foto = new org.springframework.mock.web.MockMultipartFile(
                "foto", "foto.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1});

        mockMvc.perform(multipart("/api/reportes/r-ajeno/foto").file(foto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value("/fotos/puesta-por-un-desconocido.jpg"));

        verify(agregarEvidenciaUseCase).agregarEvidencia(eq("r-ajeno"), eq("image/jpeg"), any());
    }

    // --- confirmar ---

    @Test
    void debeConfirmarReporteConLaIdentidadDeLaCabeceraYResponder200() throws Exception {
        ReporteCiudadano confirmado = new ReporteCiudadano(
                new ReporteId("r1"), new SectorId("bocagrande"), TipoReporte.SIN_AGUA,
                new Coordenada(10.39, -75.48), new HuellaDispositivo("hash-1"), AHORA,
                com.aguavigia.ctg.domain.EstadoModeracion.PENDIENTE, null, java.util.Set.of("hash-confirm"));
        given(confirmarReporte.confirmar(any(), any())).willReturn(confirmado);

        mockMvc.perform(post("/api/reportes/r1/confirmar").header("X-Dispositivo", "token-del-dispositivo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.confirmaciones").value(1));

        verify(identificar).identificar(eq("token-del-dispositivo"), isNull());
        verify(confirmarReporte).confirmar(new ReporteId("r1"), REPORTANTE.huella());
    }

    /** D7: el cuerpo de la confirmación ya no existe; si un cliente viejo manda `{"huella": ...}`, se ignora. */
    @Test
    void unaHuellaEnElCuerpoDeLaConfirmacionNoDebeSerLaIdentidad() throws Exception {
        given(confirmarReporte.confirmar(any(), any()))
                .willReturn(reporte("r1", "bocagrande", TipoReporte.SIN_AGUA, null));

        mockMvc.perform(post("/api/reportes/r1/confirmar")
                        .header("X-Dispositivo", "t")
                        .contentType("application/json")
                        .content("""
                                {"huella":"hash-confirm-hash-confirm-hash-conf"}"""))
                .andExpect(status().isOk());

        verify(confirmarReporte).confirmar(new ReporteId("r1"), REPORTANTE.huella());
    }

    @Test
    void confirmarSinIdentidadDebeResponder401() throws Exception {
        given(identificar.identificar(any(), any()))
                .willThrow(new DispositivoInvalidoException("Falta la identidad del dispositivo."));

        mockMvc.perform(post("/api/reportes/r1/confirmar"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://aguavigia.example/errores/dispositivo-invalido"));

        verify(confirmarReporte, never()).confirmar(any(), any());
    }
}
