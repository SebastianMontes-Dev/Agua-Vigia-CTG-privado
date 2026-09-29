package com.aguavigia.ctg.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeneradorSecretosSeguroAdapterTest {

    private final GeneradorSecretosSeguroAdapter generador = new GeneradorSecretosSeguroAdapter();

    @Test
    void elTokenDeEnlaceDebeSerUrlSeguroYDe256Bits() {
        String token = generador.generarTokenDeEnlace();

        // 32 bytes en Base64 URL sin relleno = 43 caracteres, aptos para un query param.
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void dosTokensNuncaDebenRepetirse() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(generador.generarTokenDeEnlace());
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void elHashDelTokenDebeSerSha256EnHexadecimalYDeterminista() {
        // Vector conocido de SHA-256 para "abc".
        assertThat(generador.hashDeTokenDeEnlace("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(generador.hashDeTokenDeEnlace("abc")).isEqualTo(generador.hashDeTokenDeEnlace("abc"));
        assertThat(generador.hashDeTokenDeEnlace("abd")).isNotEqualTo(generador.hashDeTokenDeEnlace("abc"));
    }

    @Test
    void elHashNoDebeContenerElTokenEnClaro() {
        String token = generador.generarTokenDeEnlace();

        assertThat(generador.hashDeTokenDeEnlace(token)).doesNotContain(token).hasSize(64);
    }

    @Test
    void elSecretoTotpDebeSerBase32DeLosTreintaYDosCaracteresDe160Bits() {
        assertThat(generador.generarSecretoTotp().valor()).hasSize(32).matches("[A-Z2-7]+");
    }

    @Test
    void dosSecretosTotpNuncaDebenRepetirse() {
        assertThat(generador.generarSecretoTotp()).isNotEqualTo(generador.generarSecretoTotp());
    }
}
