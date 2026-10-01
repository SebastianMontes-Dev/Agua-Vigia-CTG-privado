package com.aguavigia.ctg.infrastructure.security;

import com.aguavigia.ctg.domain.RedDeOrigen;
import com.aguavigia.ctg.domain.port.out.HashDeRedPort;
import com.aguavigia.ctg.domain.port.out.SecretosDelSistemaPort;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Base64;

/**
 * HMAC-SHA256(secreto, día + IP). Meter el día en el mensaje equivale a una sal diaria sin tener que guardar
 * ninguna: el mismo día la misma red da siempre el mismo resumen, y al día siguiente da otro, de modo que lo
 * guardado en los reportes no permite seguir a una red durante semanas.
 *
 * No es anonimato fuerte: el espacio de IPv4 es pequeño, y quien tuviera el secreto podría recorrerlo. Por eso el
 * secreto es propio de esta función, vive en `config_sistema` y no sale de allí.
 */
@Component
public class HmacHashDeRedAdapter implements HashDeRedPort {

    static final String SECRETO = "red";
    private static final String ALGORITMO = "HmacSHA256";

    private final SecretosDelSistemaPort secretos;

    public HmacHashDeRedAdapter(SecretosDelSistemaPort secretos) {
        this.secretos = secretos;
    }

    @Override
    public String hashear(String ip, LocalDate dia) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secretos.obtenerOCrear(SECRETO).getBytes(StandardCharsets.UTF_8), ALGORITMO));
            byte[] resumen = mac.doFinal((dia + "|" + RedDeOrigen.de(ip)).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(resumen);
        } catch (GeneralSecurityException imposibleEnCualquierJvm) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", imposibleEnCualquierJvm);
        }
    }
}
