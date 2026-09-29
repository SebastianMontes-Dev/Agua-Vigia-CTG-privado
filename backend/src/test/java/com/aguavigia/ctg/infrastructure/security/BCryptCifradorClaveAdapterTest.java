package com.aguavigia.ctg.infrastructure.security;

import com.aguavigia.ctg.domain.ClaveHash;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptCifradorClaveAdapterTest {

    private final BCryptCifradorClaveAdapter cifrador = new BCryptCifradorClaveAdapter(new BCryptPasswordEncoder());

    @Test
    void debeCifrarConBcryptYNoGuardarLaClaveEnClaro() {
        ClaveHash hash = cifrador.cifrar("una-clave-larga-y-variada");

        assertThat(hash.valor()).startsWith("$2").doesNotContain("una-clave-larga-y-variada");
    }

    @Test
    void dosCifradosDeLaMismaClaveDebenDiferirPorLaSal() {
        assertThat(cifrador.cifrar("una-clave-larga-y-variada").valor())
                .isNotEqualTo(cifrador.cifrar("una-clave-larga-y-variada").valor());
    }

    @Test
    void debeCoincidirConLaClaveCorrectaYRechazarLaIncorrecta() {
        ClaveHash hash = cifrador.cifrar("una-clave-larga-y-variada");

        assertThat(cifrador.coincide("una-clave-larga-y-variada", hash)).isTrue();
        assertThat(cifrador.coincide("otra-clave", hash)).isFalse();
    }

    @Test
    void unaClaveOUnHashNulosNuncaDebenCoincidir() {
        ClaveHash hash = cifrador.cifrar("una-clave-larga-y-variada");

        assertThat(cifrador.coincide(null, hash)).isFalse();
        assertThat(cifrador.coincide("una-clave-larga-y-variada", null)).isFalse();
    }

    /** RNF024: el gasto equivalente tiene que costar como una comparación real, no como no hacer nada. */
    @Test
    void gastarTiempoEquivalenteDebeTardarComoUnaComparacionReal() {
        ClaveHash hash = cifrador.cifrar("una-clave-larga-y-variada");
        cifrador.coincide("x", hash);
        cifrador.gastarTiempoEquivalente();

        long comparacion = medirNanos(() -> cifrador.coincide("otra", hash));
        long gasto = medirNanos(cifrador::gastarTiempoEquivalente);

        assertThat(gasto).isGreaterThan(comparacion / 3);
        assertThat(gasto).isGreaterThan(5_000_000L);
    }

    private static long medirNanos(Runnable accion) {
        long inicio = System.nanoTime();
        accion.run();
        return System.nanoTime() - inicio;
    }
}
