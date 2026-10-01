package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedDeOrigenTest {

    @Test
    void unaIpv4DebeQuedarIgual() {
        assertThat(RedDeOrigen.de("190.20.30.40")).isEqualTo("190.20.30.40");
    }

    /** Un abonado IPv6 recibe un /64 entero: cada dirección suya no es una red distinta. */
    @Test
    void dosIpv6DelMismoPrefijo64DebenDarLaMismaRed() {
        assertThat(RedDeOrigen.de("2800:484:1234:5678:aaaa:bbbb:cccc:dddd"))
                .isEqualTo(RedDeOrigen.de("2800:484:1234:5678:1:2:3:4"));
    }

    @Test
    void dosIpv6DePrefijosDistintosDebenDarRedesDistintas() {
        assertThat(RedDeOrigen.de("2800:484:1234:5678::1"))
                .isNotEqualTo(RedDeOrigen.de("2800:484:1234:5679::1"));
    }

    @Test
    void laFormaAbreviadaYLaCompletaDeUnaIpv6DebenDarLaMismaRed() {
        assertThat(RedDeOrigen.de("2800:484::1")).isEqualTo(RedDeOrigen.de("2800:0484:0000:0000:0000:0000:0000:0001"));
    }

    @Test
    void unaIpv4MapeadaEnIpv6DebeTratarseComoLaIpv4() {
        assertThat(RedDeOrigen.de("::ffff:190.20.30.40")).isEqualTo("190.20.30.40");
    }

    @Test
    void elIdentificadorDeZonaNoDebeCambiarLaRed() {
        assertThat(RedDeOrigen.de("fe80::1%eth0")).isEqualTo(RedDeOrigen.de("fe80::2"));
    }

    /** Nunca se resuelve un nombre: un valor que no es una IP literal se devuelve tal cual, sin consultar DNS. */
    @Test
    void unTextoQueNoEsUnaIpDebeQuedarIgualSinConsultarDns() {
        assertThat(RedDeOrigen.de("ejemplo.invalid")).isEqualTo("ejemplo.invalid");
        assertThat(RedDeOrigen.de("")).isEmpty();
        assertThat(RedDeOrigen.de(null)).isNull();
    }
}
