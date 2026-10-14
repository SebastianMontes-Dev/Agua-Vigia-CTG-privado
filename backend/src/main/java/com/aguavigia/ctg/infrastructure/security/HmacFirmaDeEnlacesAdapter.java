package com.aguavigia.ctg.infrastructure.security;

import com.aguavigia.ctg.domain.EnlaceDeRestablecimiento;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionId;
import com.aguavigia.ctg.domain.port.out.FirmaDeEnlacesPort;
import com.aguavigia.ctg.domain.port.out.SecretosDelSistemaPort;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Token = `payload.firma`, con payload = base64url(`barrio|suscripción|vencimiento`) y firma = HMAC-SHA256(secreto, payload)
 * en base64url sin relleno. El vencimiento va dentro y firmado: no se puede estirar. Usa un secreto propio, distinto del de
 * los dispositivos: un token de un uso no debe servir como el del otro.
 *
 * La comparación de firmas es en tiempo constante.
 */
@Component
public class HmacFirmaDeEnlacesAdapter implements FirmaDeEnlacesPort {

    static final String SECRETO = "enlaces-de-restablecimiento";
    private static final String ALGORITMO = "HmacSHA256";
    private static final Base64.Encoder CODIFICADOR = Base64.getUrlEncoder().withoutPadding();

    private final SecretosDelSistemaPort secretos;

    public HmacFirmaDeEnlacesAdapter(SecretosDelSistemaPort secretos) {
        this.secretos = secretos;
    }

    @Override
    public String emitir(EnlaceDeRestablecimiento enlace) {
        String contenido = enlace.sector().valor() + "|" + enlace.suscripcion().valor() + "|" + enlace.venceEn().getEpochSecond();
        String payload = CODIFICADOR.encodeToString(contenido.getBytes(StandardCharsets.UTF_8));
        return payload + "." + firmar(payload);
    }

    @Override
    public Optional<EnlaceDeRestablecimiento> verificar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int punto = token.indexOf('.');
        if (punto <= 0 || punto == token.length() - 1 || punto != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        String payload = token.substring(0, punto);
        byte[] esperada = firmar(payload).getBytes(StandardCharsets.UTF_8);
        byte[] recibida = token.substring(punto + 1).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperada, recibida)) {
            return Optional.empty();
        }
        try {
            String contenido = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            String[] partes = contenido.split("\\|", -1);
            if (partes.length != 3) {
                return Optional.empty();
            }
            return Optional.of(new EnlaceDeRestablecimiento(new SectorId(partes[0]), new SuscripcionId(partes[1]),
                    Instant.ofEpochSecond(Long.parseLong(partes[2]))));
        } catch (IllegalArgumentException | java.time.DateTimeException contenidoIlegible) {
            return Optional.empty();
        }
    }

    private String firmar(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secretos.obtenerOCrear(SECRETO).getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return CODIFICADOR.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException imposibleEnCualquierJvm) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", imposibleEnCualquierJvm);
        }
    }
}
