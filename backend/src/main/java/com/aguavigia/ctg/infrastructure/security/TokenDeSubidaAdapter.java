package com.aguavigia.ctg.infrastructure.security;

import com.aguavigia.ctg.domain.port.out.TokenDeSubidaPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 32 bytes de {@link SecureRandom} (256 bits, imposibles de adivinar) y su SHA-256 para guardarlos. */
@Component
public class TokenDeSubidaAdapter implements TokenDeSubidaPort {

    private final SecureRandom azar = new SecureRandom();

    @Override
    public String nuevo() {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hash(String tokenEnClaro) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(tokenEnClaro.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposibleEnCualquierJvm) {
            throw new IllegalStateException("SHA-256 no disponible", imposibleEnCualquierJvm);
        }
    }
}
