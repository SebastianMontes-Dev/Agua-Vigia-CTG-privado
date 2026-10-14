package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.AdministrarCuentaUseCase;
import com.aguavigia.ctg.domain.port.in.ConsultarCuentasUseCase;
import com.aguavigia.ctg.domain.port.in.InvitarUsuarioUseCase;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUsuariosController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class AdminUsuariosControllerTest {

    private static final String TOKEN = "token-admin";
    private static final Instant AHORA = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarCuentasUseCase cuentas;

    @MockitoBean
    private AdministrarCuentaUseCase administrar;

    @MockitoBean
    private InvitarUsuarioUseCase invitar;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @Test
    void llamadaSinTokenDebeResponder401EnFormatoRfc7807() throws Exception {
        mockMvc.perform(get("/api/veedor/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void usuarioSinPermisoGestionarUsuariosDebeResponder403EnFormatoRfc7807() throws Exception {
        given(jwtProvider.validar(TOKEN))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.VER_PANEL)));
        given(revocacion.revocadasAntesDe(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/api/veedor/usuarios")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void usuarioConPermisoGestionarUsuariosPuedeListarUsuarios() throws Exception {
        given(jwtProvider.validar(TOKEN))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.GESTIONAR_USUARIOS)));
        given(revocacion.revocadasAntesDe(any())).willReturn(Optional.empty());

        Usuario usuario = new Usuario(
                new UsuarioId("22222222-2222-2222-2222-222222222222"),
                new CorreoElectronico("veedor@aguavigia.ctg"),
                "Veedor CTG",
                new ClaveHash("$2a$10$abcdefghijklmnopqrstuu"),
                EstadoCuenta.ACTIVA,
                PermisosEfectivos.deRol(RolVeedor.VEEDOR),
                null,
                AHORA,
                AHORA);

        given(cuentas.listar(any(), any(), anyInt(), anyInt()))
                .willReturn(new Pagina<>(List.of(usuario), 0, 20, 1));

        mockMvc.perform(get("/api/veedor/usuarios")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correo").value("veedor@aguavigia.ctg"))
                .andExpect(jsonPath("$[0].nombre").value("Veedor CTG"))
                .andExpect(jsonPath("$[0].rol").value("VEEDOR"));
    }

    @Test
    void elEnlaceALaSiguientePaginaDeCuentasDebeConservarElFiltroDeEstado() throws Exception {
        given(jwtProvider.validar(TOKEN))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.GESTIONAR_USUARIOS)));
        given(revocacion.revocadasAntesDe(any())).willReturn(Optional.empty());
        given(cuentas.listar(EstadoCuenta.PENDIENTE_APROBACION, null, 0, 2))
                .willReturn(new Pagina<>(List.of(), 0, 2, 5));

        mockMvc.perform(get("/api/veedor/usuarios").param("estado", "pendiente_aprobacion").param("tamano", "2")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Link",
                        "</api/veedor/usuarios?pagina=1&tamano=2&estado=PENDIENTE_APROBACION>; rel=\"next\""));
    }

    @Test
    void debeFiltrarLasCuentasPorBarrioYMostrarElBarrioDeCadaUna() throws Exception {
        given(jwtProvider.validar(TOKEN))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.GESTIONAR_USUARIOS)));
        given(revocacion.revocadasAntesDe(any())).willReturn(Optional.empty());
        Usuario vecino = new Usuario(new UsuarioId("33333333-3333-3333-3333-333333333333"),
                new CorreoElectronico("vecina@ejemplo.org"), "Vecina de Manga",
                new ClaveHash("$2a$10$abcdefghijklmnopqrstuu"), EstadoCuenta.ACTIVA,
                PermisosEfectivos.deRol(RolVeedor.OBSERVADOR), null, AHORA, AHORA, new SectorId("manga"));
        given(cuentas.listar(null, new SectorId("manga"), 0, Pagina.TAMANO_POR_DEFECTO))
                .willReturn(new Pagina<>(List.of(vecino), 0, Pagina.TAMANO_POR_DEFECTO, 1));

        mockMvc.perform(get("/api/veedor/usuarios").param("barrioId", "manga")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].barrioId").value("manga"));
    }

    /** ADR-094: con decenas de miles de cuentas sintéticas en la base, el administrador tiene que poder distinguirlas de las reales. */
    @Test
    void elListadoDebeDistinguirLasCuentasSinteticasDeLasReales() throws Exception {
        given(jwtProvider.validar(TOKEN))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.GESTIONAR_USUARIOS)));
        given(revocacion.revocadasAntesDe(any())).willReturn(Optional.empty());
        Usuario sintetica = Usuario.sinteticoComoVecino(new UsuarioId("44444444-4444-4444-4444-444444444444"),
                new CorreoElectronico("cuenta-sintetica-000001@demo.aguavigia.invalid"), "Cuenta sintética 000001",
                new SectorId("manga"), AHORA).aceptarInvitacion(new ClaveHash("$2a$10$abcdefghijklmnopqrstuu"), AHORA);
        Usuario real = new Usuario(new UsuarioId("55555555-5555-5555-5555-555555555555"),
                new CorreoElectronico("ana@ejemplo.org"), "Ana", new ClaveHash("$2a$10$abcdefghijklmnopqrstuu"),
                EstadoCuenta.ACTIVA, PermisosEfectivos.deRol(RolVeedor.OBSERVADOR), null, AHORA, AHORA);
        given(cuentas.listar(any(), any(), anyInt(), anyInt()))
                .willReturn(new Pagina<>(List.of(sintetica, real), 0, 20, 2));

        mockMvc.perform(get("/api/veedor/usuarios").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sintetica").value(true))
                .andExpect(jsonPath("$[1].sintetica").value(false));
    }

    @Test
    void invitarConUnBarrioInexistenteDebeResponder400() throws Exception {
        given(jwtProvider.validar(TOKEN))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.GESTIONAR_USUARIOS)));
        given(revocacion.revocadasAntesDe(any())).willReturn(Optional.empty());
        given(invitar.invitar(any(), any(), any(), any(), any()))
                .willThrow(new IllegalArgumentException("No existe el barrio 'no-existe'"));

        mockMvc.perform(post("/api/veedor/usuarios/invitaciones")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"beto@ejemplo.org\",\"nombre\":\"Beto\",\"rol\":\"OBSERVADOR\",\"barrioId\":\"no-existe\"}")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest());
    }
}
