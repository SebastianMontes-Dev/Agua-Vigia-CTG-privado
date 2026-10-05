package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;

import java.util.Optional;

public interface UsuarioRepository {

    Usuario guardar(Usuario usuario);

    /**
     * Guarda solo si la cuenta sigue como se leyó (su {@code actualizadoEn} no cambió). Quien lee una cuenta, la
     * modifica y la guarda no debe pisar lo que otro escribió entre medias: una suspensión del administrador no puede
     * deshacerse porque el vecino editaba su perfil a la vez. Si cambió o ya no existe lanza
     * {@link IllegalStateException}, que la API responde como 409 para que el cliente reintente.
     */
    Usuario guardarSiNoCambio(Usuario usuario, java.time.Instant actualizadoEnLeido);

    Optional<Usuario> buscarPorId(UsuarioId id);

    /** El correo es la identidad de acceso: el adaptador lo busca normalizado a minúsculas. */
    Optional<Usuario> buscarPorCorreo(CorreoElectronico correo);

    boolean existePorCorreo(CorreoElectronico correo);

    Pagina<Usuario> listar(EstadoCuenta filtroEstado, SectorId filtroBarrio, int pagina, int tamano);

    /**
     * Lo usa la guarda del último administrador: sin este conteo, suspender o despromover a la
     * única cuenta ADMIN activa deja el sistema sin nadie capaz de volver a otorgar permisos, y
     * eso no se arregla desde la aplicación — hay que ir a la base de datos a mano.
     */
    long contarActivosPorRol(RolVeedor rol);
}
