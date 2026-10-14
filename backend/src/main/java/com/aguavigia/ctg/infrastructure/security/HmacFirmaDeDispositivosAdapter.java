package com.aguavigia.ctg.infrastructure.security;

import com.aguavigia.ctg.domain.DispositivoId;
import com.aguavigia.ctg.domain.port.out.FirmaDeDispositivosPort;
import com.aguavigia.ctg.domain.port.out.SecretosDelSistemaPort;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * Token = `id.firma`, con firma = HMAC-SHA256(secreto, id) en base64url sin relleno. No lleva
 * caducidad: la identidad de un dispositivo dura lo que dure su registro en `dispositivos` (que
 * vence 12 meses después de su último uso), y quien la pierda simplemente pide otra.
 *
 * La comparación de firmas es en tiempo constante: con una comparación normal, el tiempo de
 * respuesta diría cuántos caracteres de la firma acertó quien la prueba.
 */
@Component
public class HmacFirmaDeDispositivosAdapter implements FirmaDeDispositivosPort {

    static final String SECRETO = "dispositivos";
    private static final String ALGORITMO = "HmacSHA256";

    private final SecretosDelSistemaPort secretos;

    public HmacFirmaDeDispositivosAdapter(SecretosDelSistemaPort secretos) {
        this.secretos = secretos;
    }

    @Override
    public String emitir(DispositivoId id) {
        return id.valor() + "." + firmar(id.valor());
    }

    @Override
    public Optional<DispositivoId> verificar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int punto = token.indexOf('.');
        if (punto <= 0 || punto == token.length() - 1 || punto != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        String id = token.substring(0, punto);
        String firmaRecibida = token.substring(punto + 1);

        boolean coincide = MessageDigest.isEqual(
                firmar(id).getBytes(StandardCharsets.UTF_8), firmaRecibida.getBytes(StandardCharsets.UTF_8));
        return coincide ? Optional.of(new DispositivoId(id)) : Optional.empty();
    }

    private String firmar(String id) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secretos.obtenerOCrear(SECRETO).getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(id.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException imposibleEnCualquierJvm) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", imposibleEnCualquierJvm);
        }
    }
}
