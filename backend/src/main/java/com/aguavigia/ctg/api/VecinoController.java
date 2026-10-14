package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.CredencialVecino;
import com.aguavigia.ctg.api.dto.PerfilVecinoRespuesta;
import com.aguavigia.ctg.api.dto.SesionVecino;
import com.aguavigia.ctg.api.dto.SolicitudPerfilVecino;
import com.aguavigia.ctg.api.dto.SolicitudVerificacionBarrio;
import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.SesionSinCuentaException;
import com.aguavigia.ctg.domain.port.in.ActualizarPerfilVecinoUseCase;
import com.aguavigia.ctg.domain.port.in.AutenticarUsuarioUseCase;
import com.aguavigia.ctg.domain.port.in.CerrarSesionUseCase;
import com.aguavigia.ctg.domain.port.in.ConsultarCuentasUseCase;
import com.aguavigia.ctg.domain.port.in.VerificarBarrioVecinoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingreso y perfil del vecino registrado. El controlador solo traduce HTTP a casos de uso; las
 * reglas (bloqueo por intentos, que solo entren cuentas VECINO, qué cambia al mudarse de barrio)
 * viven en los servicios.
 *
 * Todo menos el ingreso exige el permiso GESTIONAR_PERFIL_PROPIO, que solo tiene el rol VECINO (y
 * el ADMIN, que hereda todos): una sesión de OBSERVADOR o VEEDOR no entra aquí.
 */
@Tag(name = "Vecinos", description = "Registro, ingreso y perfil de los vecinos")
@RestController
@RequestMapping(value = "/api/vecino", produces = MediaType.APPLICATION_JSON_VALUE)
public class VecinoController {

    private final AutenticarUsuarioUseCase autenticar;
    private final CerrarSesionUseCase cerrarSesion;
    private final ConsultarCuentasUseCase cuentas;
    private final ActualizarPerfilVecinoUseCase actualizarPerfil;
    private final VerificarBarrioVecinoUseCase verificarBarrio;

    public VecinoController(AutenticarUsuarioUseCase autenticar,
                            CerrarSesionUseCase cerrarSesion,
                            ConsultarCuentasUseCase cuentas,
                            ActualizarPerfilVecinoUseCase actualizarPerfil,
                            VerificarBarrioVecinoUseCase verificarBarrio) {
        this.autenticar = autenticar;
        this.cerrarSesion = cerrarSesion;
        this.cuentas = cuentas;
        this.actualizarPerfil = actualizarPerfil;
        this.verificarBarrio = verificarBarrio;
    }

    @Operation(summary = "Iniciar sesion como vecino",
            description = """
                    Devuelve un token JWT valido por 8 horas que sirve en `/api/vecino/**` (y, del panel, solo en `GET /api/veedor/yo` y `POST /api/veedor/sesion/cierre`). Una
                    cuenta del panel no entra por aqui, ni una de vecino por `/api/veedor/sesion`:
                    en los dos casos la respuesta es la misma 401 que ante una clave incorrecta.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credencial correcta, token emitido"),
            @ApiResponse(responseCode = "401", description = "Credencial incorrecta"),
            @ApiResponse(responseCode = "403", description = "La cuenta esta suspendida (si aun no eligio su clave, responde 401 como con una clave incorrecta)"),
            @ApiResponse(responseCode = "423", description = "Cuenta bloqueada por intentos fallidos"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos desde esta IP")
    })
    @PostMapping("/sesion")
    public SesionVecino iniciarSesion(@Valid @RequestBody CredencialVecino credencial,
                                      HttpServletRequest peticion) {
        return SesionVecino.de(autenticar.autenticarVecino(
                new CorreoElectronico(credencial.correo()),
                credencial.clave(),
                ContextoHttp.de(peticion)));
    }

    @Operation(summary = "Verificar el barrio con la ubicacion del momento",
            description = """
                    Compara la coordenada con el poligono del barrio que declaraste. Si cae dentro, el
                    barrio queda verificado con su fecha; la coordenada se descarta y no se guarda.
                    Maximo 3 intentos por dia (429 con `Retry-After`). Si ya estaba verificado, no hace
                    nada y devuelve el perfil. Es una senal blanda: sube el costo de votar desde un
                    barrio ajeno, no prueba identidad.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil con el barrio verificado"),
            @ApiResponse(responseCode = "400", description = "Coordenada fuera de rango o precision ausente o negativa"),
            @ApiResponse(responseCode = "422", description = """
                    `ubicacion-fuera-del-barrio` (la ubicacion no cae en tu barrio declarado) o \
                    `ubicacion-imprecisa` (precision peor que 200 m; reintenta con GPS)"""),
            @ApiResponse(responseCode = "429", description = "Ya usaste los 3 intentos de hoy")
    })
    @PostMapping("/verificacion-barrio")
    @PreAuthorize("hasAuthority('PERM_GESTIONAR_PERFIL_PROPIO')")
    public PerfilVecinoRespuesta verificarBarrio(@Valid @RequestBody SolicitudVerificacionBarrio solicitud,
                                                 HttpServletRequest peticion) {
        return PerfilVecinoRespuesta.de(verificarBarrio.verificar(
                ContextoHttp.usuarioActual(),
                new Coordenada(solicitud.coordenada().latitud(), solicitud.coordenada().longitud()),
                solicitud.precisionMetros(),
                ContextoHttp.de(peticion)));
    }

    @Operation(summary = "Cerrar sesion",
            description = "Revoca en el servidor todas las sesiones vivas de la cuenta, no solo la de este navegador.")
    @PostMapping("/sesion/cierre")
    @PreAuthorize("hasAuthority('PERM_GESTIONAR_PERFIL_PROPIO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrar() {
        cerrarSesion.cerrar(ContextoHttp.usuarioActual());
    }

    @Operation(summary = "Perfil del vecino con la sesion",
            description = "Devuelve el estado vigente en la base de datos, no lo que dice el token.")
    @GetMapping("/yo")
    @PreAuthorize("hasAuthority('PERM_GESTIONAR_PERFIL_PROPIO')")
    public PerfilVecinoRespuesta yo() {
        return cuentas.buscar(ContextoHttp.usuarioActual())
                .map(PerfilVecinoRespuesta::de)
                .orElseThrow(() -> new SesionSinCuentaException("La sesion no corresponde a una cuenta de vecino"));
    }

    @Operation(summary = "Actualizar el perfil",
            description = """
                    Cambia nombre, barrio y la casilla de avisos; solo se aplica lo que viene. Cambiar
                    de barrio anula la verificacion de barrio anterior.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
            @ApiResponse(responseCode = "400", description = "Nombre invalido o barrio inexistente")
    })
    @PatchMapping("/perfil")
    @PreAuthorize("hasAuthority('PERM_GESTIONAR_PERFIL_PROPIO')")
    public PerfilVecinoRespuesta actualizarPerfil(@Valid @RequestBody SolicitudPerfilVecino solicitud,
                                                  HttpServletRequest peticion) {
        return PerfilVecinoRespuesta.de(actualizarPerfil.actualizar(
                ContextoHttp.usuarioActual(), solicitud.aDominio(), ContextoHttp.de(peticion)));
    }
}
