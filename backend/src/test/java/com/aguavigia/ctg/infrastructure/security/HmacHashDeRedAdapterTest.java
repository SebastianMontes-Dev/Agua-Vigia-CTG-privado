package com.aguavigia.ctg.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HmacHashDeRedAdapterTest {

    private static final String SECRETO = "secreto-de-red-de-prueba-0123456789-abcdefghij";
    private static final LocalDate HOY = LocalDate.of(2026, 10, 1);

    private final HmacHashDeRedAdapter hash = new HmacHashDeRedAdapter(nombre -> SECRETO);

    @Test
    void lamismaIpElMismoDiaDebeDarElMismoHash() {
        assertThat(hash.hashear("190.20.30.40", HOY)).isEqualTo(hash.hashear("190.20.30.40", HOY));
    }

    @Test
    void ipsDistintasElMismoDiaDebenDarHashesDistintos() {
        assertThat(hash.hashear("190.20.30.40", HOY)).isNotEqualTo(hash.hashear("190.20.30.41", HOY));
    }

    /** Rotar la dirección dentro del /64 de un abonado IPv6 no fabrica redes distintas. */
    @Test
    void dosIpv6DelMismoPrefijo64DebenDarElMismoHash() {
        assertThat(hash.hashear("2800:484:1234:5678:aaaa:bbbb:cccc:dddd", HOY))
                .isEqualTo(hash.hashear("2800:484:1234:5678:1:2:3:4", HOY));
    }

    /** La sal es diaria: la misma red un día distinto no se puede reconocer. */
    @Test
    void laMismaIpEnOtroDiaDebeDarOtroHash() {
        assertThat(hash.hashear("190.20.30.40", HOY)).isNotEqualTo(hash.hashear("190.20.30.40", HOY.plusDays(1)));
    }

    @Test
    void elHashNoDebeContenerLaIpNiElSecreto() {
        String resultado = hash.hashear("190.20.30.40", HOY);

        assertThat(resultado).doesNotContain("190.20.30.40").doesNotContain(SECRETO);
        assertThat(resultado).matches("[A-Za-z0-9_-]{20,}");
    }

    @Test
    void otroSecretoDebeDarOtroHash() {
        HmacHashDeRedAdapter ajeno = new HmacHashDeRedAdapter(nombre -> "otro-secreto-de-red-completamente-distinto-12345");

        assertThat(ajeno.hashear("190.20.30.40", HOY)).isNotEqualTo(hash.hashear("190.20.30.40", HOY));
    }

    /** Un secreto propio de la red: no se comparte con el de los tokens de dispositivo. */
    @Test
    void debePedirUnSecretoPropioDeRed() {
        List<String> pedidos = new ArrayList<>();
        new HmacHashDeRedAdapter(nombre -> {
            pedidos.add(nombre);
            return SECRETO;
        }).hashear("190.20.30.40", HOY);

        assertThat(pedidos).isNotEmpty().allMatch("red"::equals);
    }
}
