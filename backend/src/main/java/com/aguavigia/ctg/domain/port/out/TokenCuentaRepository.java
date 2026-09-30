package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.TokenCuenta;
import com.aguavigia.ctg.domain.UsuarioId;

import java.util.Optional;

public interface TokenCuentaRepository {

    TokenCuenta guardar(TokenCuenta token);

    /** Se busca por hash, nunca por el valor en claro: el claro solo existe dentro del correo. */
    Optional<TokenCuenta> buscarPorHash(String hash);

    /**
     * Invalida los tokens vivos de ese tipo antes de emitir uno nuevo. Sin esto, pedir tres veces
     * "olvidé mi clave" deja tres enlaces válidos a la vez, y basta con que se filtre el más viejo.
     */
    void invalidarVigentes(UsuarioId usuarioId, TipoTokenCuenta tipo);

    /**
     * Marca el token como usado solo si sigue sin usar y sin vencer, en una única operación atómica. Devuelve
     * si lo consiguió: de dos peticiones simultáneas con el mismo enlace solo una gana, y la otra recibe
     * falso. Con leer, comprobar y guardar por separado pasaban las dos.
     */
    boolean marcarUsadoSiVigente(String hash, java.time.Instant ahora);
}
