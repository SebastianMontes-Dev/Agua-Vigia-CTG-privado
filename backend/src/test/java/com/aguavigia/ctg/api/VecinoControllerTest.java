package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.AlcanceSesion;
import com.aguavigia.ctg.domain.CambiosDePerfil;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.Consentimiento;
import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.CredencialInvalidaException;
import com.aguavigia.ctg.domain.UbicacionFueraDelBarrioException;
import com.aguavigia.ctg.domain.UbicacionImprecisaException;
import com.aguavigia.ctg.domain.port.in.VerificarBarrioVecinoUseCase;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SesionEmitida;
import com.aguavigia.ctg.domain.TipoConsentimiento;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.ActualizarPerfilVecinoUseCase;
import com.aguavigia.ctg.domain.port.in.AutenticarUsuarioUseCase;
import com.aguavigia.ctg.domain.port.in.CerrarSesionUseCase;
import com.aguavigia.ctg.domain.port.in.ConsultarCuentasUseCase;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VecinoController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class VecinoControllerTest {

    private static final String TOKEN = "token-de-prueba";
    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final ClaveHash HASH =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    private static final UsuarioId ID = new UsuarioId(AutenticacionDePrueba.USUARIO_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutenticarUsuarioUseCase autenticar;

    @MockitoBean
    private CerrarSesionUseCase cerrarSesion;

    @MockitoBean
    private ConsultarCuentasUseCase cuentas;

    @MockitoBean
    private ActualizarPerfilVecinoUseCase actualizarPerfil;

    @MockitoBean
    private VerificarBarrioVecinoUseCase verificarBarrio;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @BeforeEach
    void sesion() {
        given(revocacion.revocadasAntesDe(any())).willReturn(Optional.empty());
    }

    private void autenticadoCon(Permiso... permisos) {
        given(jwtProvider.validar(TOKEN)).willReturn(Optional.of(AutenticacionDePrueba.sesionCon(permisos)));
    }

    private static Usuario vecino() {
        return Usuario.registradoComoVecino(ID, new CorreoElectronico("vecina@ejemplo.org"), "Vecina", HASH,
                        new SectorId("manga"),
                        List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "2026-10-v1", AHORA)), AHORA)
                .verificarCorreo(AHORA);
    }

    // --- POST /api/vecino/sesion ---

    @Test
    void iniciarSesionSinTokenDebeEmitirLaSesionDelVecino() throws Exception {
        given(autenticar.autenticarVecino(eq(new CorreoElectronico("vecina@ejemplo.org")),
                eq("la-clave-de-hoy"), any()))
                .willReturn(SesionEmitida.de(vecino(), "jwt-del-vecino", AlcanceSesion.COMPLETO));

        mockMvc.perform(post("/api/vecino/sesion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"vecina@ejemplo.org\",\"clave\":\"la-clave-de-hoy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-del-vecino"))
                .andExpect(jsonPath("$.usuarioId").value(AutenticacionDePrueba.USUARIO_ID))
                .andExpect(jsonPath("$.nombre").value("Vecina"))
                .andExpect(jsonPath("$.permisos[0]").value("GESTIONAR_PERFIL_PROPIO"))
                .andExpect(jsonPath("$.permisos.length()").value(1));
    }

    @Test
    void unaCredencialIncorrectaDebeResponder401() throws Exception {
        given(autenticar.autenticarVecino(any(), any(), any()))
                .willThrow(new CredencialInvalidaException("Correo o clave incorrectos."));

        mockMvc.perform(post("/api/vecino/sesion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"vecina@ejemplo.org\",\"clave\":\"otra\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unCorreoMalFormadoEnElIngresoDebeResponder400() throws Exception {
        mockMvc.perform(post("/api/vecino/sesion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"no-es-correo\",\"clave\":\"x\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(autenticar);
    }

    // --- GET /api/vecino/yo ---

    @Test
    void yoSinTokenDebeResponder401() throws Exception {
        mockMvc.perform(get("/api/vecino/yo")).andExpect(status().isUnauthorized());
    }

    /** Una sesión del panel (OBSERVADOR) no tiene el permiso del vecino: no entra a su perfil. */
    @Test
    void yoConUnPermisoSoloDelPanelDebeResponder403() throws Exception {
        autenticadoCon(Permiso.VER_PANEL);

        mockMvc.perform(get("/api/vecino/yo").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cuentas);
    }

    @Test
    void yoDebeDevolverElPerfilSinClaveNiSecretos() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);
        given(cuentas.buscar(ID)).willReturn(Optional.of(vecino().verificarBarrio(AHORA)));

        mockMvc.perform(get("/api/vecino/yo").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(AutenticacionDePrueba.USUARIO_ID))
                .andExpect(jsonPath("$.correo").value("vecina@ejemplo.org"))
                .andExpect(jsonPath("$.nombre").value("Vecina"))
                .andExpect(jsonPath("$.barrioId").value("manga"))
                .andExpect(jsonPath("$.barrioVerificado").value(true))
                .andExpect(jsonPath("$.recibeAvisos").value(false))
                .andExpect(jsonPath("$.consentimientos[0].tipo").value("PRIVACIDAD"))
                .andExpect(jsonPath("$.consentimientos[0].version").value("2026-10-v1"))
                .andExpect(jsonPath("$.claveHash").doesNotExist())
                .andExpect(jsonPath("$.segundoFactorActivo").doesNotExist());
    }

    @Test
    void yoDeUnaCuentaQueNoEsDeVecinoDebeResponder401() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);
        given(cuentas.buscar(ID)).willReturn(Optional.of(
                Usuario.invitado(ID, new CorreoElectronico("admin@ejemplo.org"), "Admin",
                        com.aguavigia.ctg.domain.RolVeedor.ADMIN, AHORA)));

        mockMvc.perform(get("/api/vecino/yo").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
    }

    // --- PATCH /api/vecino/perfil ---

    @Test
    void actualizarElPerfilSinTokenDebeResponder401() throws Exception {
        mockMvc.perform(patch("/api/vecino/perfil").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actualizarElPerfilDebePasarSoloLoQueVieneYDevolverElPerfil() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);
        given(actualizarPerfil.actualizar(eq(ID), any(), any()))
                .willReturn(vecino().mudarDeBarrio(new SectorId("crespo"), AHORA.plusSeconds(5)));

        mockMvc.perform(patch("/api/vecino/perfil").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barrioId\":\"crespo\",\"recibirAvisos\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barrioId").value("crespo"));

        verify(actualizarPerfil).actualizar(eq(ID),
                eq(new CambiosDePerfil(null, new SectorId("crespo"), true)), any());
    }

    @Test
    void unNombreDemasiadoCortoDebeResponder400() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);

        mockMvc.perform(patch("/api/vecino/perfil").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"A\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(actualizarPerfil);
    }

    @Test
    void unPermisoSoloDelPanelNoDebePoderActualizarUnPerfil() throws Exception {
        autenticadoCon(Permiso.VER_PANEL);

        mockMvc.perform(patch("/api/vecino/perfil").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Ana\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(actualizarPerfil);
    }

    // --- POST /api/vecino/verificacion-barrio ---

    private static final String RUTA_VERIFICACION = "/api/vecino/verificacion-barrio";
    private static final String UBICACION = """
            {"coordenada": {"latitud": 10.41234, "longitud": -75.54321}, "precisionMetros": 25.5}""";

    @Test
    void verificarElBarrioDebePasarLaUbicacionYDevolverElPerfilVerificado() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);
        given(verificarBarrio.verificar(eq(ID), any(), org.mockito.ArgumentMatchers.anyDouble(), any()))
                .willReturn(vecino().verificarBarrio(AHORA.plusSeconds(5)));

        mockMvc.perform(post(RUTA_VERIFICACION).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(UBICACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barrioVerificado").value(true));

        verify(verificarBarrio).verificar(eq(ID), eq(new Coordenada(10.41234, -75.54321)), eq(25.5), any());
    }

    @Test
    void verificarElBarrioSinTokenDebeResponder401() throws Exception {
        mockMvc.perform(post(RUTA_VERIFICACION).contentType(MediaType.APPLICATION_JSON).content(UBICACION))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(verificarBarrio);
    }

    @Test
    void unPermisoSoloDelPanelNoDebePoderVerificarUnBarrio() throws Exception {
        autenticadoCon(Permiso.VER_PANEL);

        mockMvc.perform(post(RUTA_VERIFICACION).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(UBICACION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(verificarBarrio);
    }

    @Test
    void unaUbicacionFueraDelBarrioDebeResponder422ConSuTipo() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);
        given(verificarBarrio.verificar(any(), any(), org.mockito.ArgumentMatchers.anyDouble(), any()))
                .willThrow(new UbicacionFueraDelBarrioException("La ubicación no cae dentro del barrio que declaraste (manga)."));

        mockMvc.perform(post(RUTA_VERIFICACION).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(UBICACION))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://aguavigia.example/errores/ubicacion-fuera-del-barrio"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("manga")));
    }

    @Test
    void unaUbicacionImprecisaDebeResponder422ConSuTipo() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);
        given(verificarBarrio.verificar(any(), any(), org.mockito.ArgumentMatchers.anyDouble(), any()))
                .willThrow(new UbicacionImprecisaException("La ubicación llegó con una precisión de 900 m."));

        mockMvc.perform(post(RUTA_VERIFICACION).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(UBICACION))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://aguavigia.example/errores/ubicacion-imprecisa"));
    }

    @Test
    void sinIntentosDelDiaDebeResponder429ConRetryAfter() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);
        given(verificarBarrio.verificar(any(), any(), org.mockito.ArgumentMatchers.anyDouble(), any()))
                .willThrow(new com.aguavigia.ctg.domain.LimiteDePeticionesExcedidoException(
                        "Ya usaste los 3 intentos de verificación de hoy.", 18000));

        mockMvc.perform(post(RUTA_VERIFICACION).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(UBICACION))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Retry-After", "18000"));
    }

    @Test
    void unaLatitudFueraDeRangoDebeResponder400() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);

        mockMvc.perform(post(RUTA_VERIFICACION).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coordenada\": {\"latitud\": 95, \"longitud\": -75.5}, \"precisionMetros\": 10}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(verificarBarrio);
    }

    @Test
    void sinLaPrecisionDebeResponder400PorqueSinEllaNoSePuedeJuzgarLaUbicacion() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);

        mockMvc.perform(post(RUTA_VERIFICACION).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coordenada\": {\"latitud\": 10.4, \"longitud\": -75.5}}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(verificarBarrio);
    }

    @Test
    void sinLaCoordenadaDebeResponder400() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);

        mockMvc.perform(post(RUTA_VERIFICACION).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"precisionMetros\": 10}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(verificarBarrio);
    }

    // --- POST /api/vecino/sesion/cierre ---

    @Test
    void cerrarSesionDebeRevocarLasSesionesDeLaCuentaYResponder204() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_PERFIL_PROPIO);

        mockMvc.perform(post("/api/vecino/sesion/cierre").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(cerrarSesion).cerrar(ID);
    }

    @Test
    void cerrarSesionSinTokenDebeResponder401() throws Exception {
        mockMvc.perform(post("/api/vecino/sesion/cierre")).andExpect(status().isUnauthorized());

        verifyNoInteractions(cerrarSesion);
    }
}
