package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.FotoLeida;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.port.in.ObtenerFotoUseCase;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FotoController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class FotoControllerTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
    private static final String TOKEN = "Bearer token-de-veedor";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObtenerFotoUseCase obtenerFoto;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    private void autenticarCon(Permiso... permisos) {
        given(revocacion.revocadasAntesDe(org.mockito.ArgumentMatchers.any())).willReturn(Optional.empty());
        given(jwtProvider.validar("token-de-veedor"))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(permisos)));
    }

    // --- pública ---

    @Test
    void unaFotoPublicaSeSirveSinSesionConSuTipoYSinPermitirQueElNavegadorAdivine() throws Exception {
        given(obtenerFoto.paraPublico("abc.jpg")).willReturn(Optional.of(new FotoLeida(JPEG, "image/jpeg")));

        mockMvc.perform(get("/api/fotos/abc.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(JPEG));
    }

    /** No existe, no está aprobada o se descartó: el público recibe lo mismo, para que no pueda sondear qué fotos hay. */
    @Test
    void unaFotoQueNoSePuedeVerDebeResponder404() throws Exception {
        given(obtenerFoto.paraPublico("abc.jpg")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/fotos/abc.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void laRutaPublicaNoDebeUsarElMetodoDelPanel() throws Exception {
        given(obtenerFoto.paraPublico("abc.jpg")).willReturn(Optional.empty());
        given(obtenerFoto.paraPanel("abc.jpg")).willReturn(Optional.of(new FotoLeida(JPEG, "image/jpeg")));

        mockMvc.perform(get("/api/fotos/abc.jpg"))
                .andExpect(status().isNotFound());
    }

    // --- panel ---

    @Test
    void elPanelVeLaFotoDeCualquierReporte() throws Exception {
        autenticarCon(Permiso.VER_PANEL);
        given(obtenerFoto.paraPanel("abc.png")).willReturn(Optional.of(new FotoLeida(JPEG, "image/png")));

        mockMvc.perform(get("/api/veedor/fotos/abc.png").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void elPanelSinSesionDebeResponder401() throws Exception {
        mockMvc.perform(get("/api/veedor/fotos/abc.png"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elPanelSinElPermisoDeVerDebeResponder403() throws Exception {
        autenticarCon(Permiso.MODERAR_REPORTES);

        mockMvc.perform(get("/api/veedor/fotos/abc.png").header("Authorization", TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaFotoInexistenteEnElPanelDebeResponder404() throws Exception {
        autenticarCon(Permiso.VER_PANEL);
        given(obtenerFoto.paraPanel("abc.png")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/veedor/fotos/abc.png").header("Authorization", TOKEN))
                .andExpect(status().isNotFound());
    }

    /** La ruta vieja ya no existe: todo lo que la seguridad no declara se deniega. */
    @Test
    void laRutaVieja_DeFotos_YaNoSeSirve() throws Exception {
        mockMvc.perform(get("/fotos/abc.jpg"))
                .andExpect(status().isUnauthorized());
    }
}
