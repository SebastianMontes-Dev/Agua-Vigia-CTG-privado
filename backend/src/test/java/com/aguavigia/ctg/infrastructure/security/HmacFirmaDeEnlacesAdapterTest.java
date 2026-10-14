package com.aguavigia.ctg.infrastructure.security;

import com.aguavigia.ctg.domain.EnlaceDeRestablecimiento;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HmacFirmaDeEnlacesAdapterTest {

    private static final String SECRETO = "secreto-de-prueba-que-nadie-mas-conoce-0123456789";
    private static final EnlaceDeRestablecimiento ENLACE = new EnlaceDeRestablecimiento(
            new SectorId("manga"), new SuscripcionId("s-1"), Instant.parse("2026-08-21T15:00:00Z"));

    private final HmacFirmaDeEnlacesAdapter firma = new HmacFirmaDeEnlacesAdapter(nombre -> SECRETO);

    @Test
    void unEnlaceEmitidoDebeVerificarseYDevolverLoMismo() {
        assertThat(firma.verificar(firma.emitir(ENLACE))).contains(ENLACE);
    }

    @Test
    void elTokenSoloDebeUsarCaracteresSegurosParaUnaUrl() {
        assertThat(firma.emitir(ENLACE)).matches("[A-Za-z0-9._-]+");
    }

    @Test
    void elTokenNoDebeContenerElSecreto() {
        assertThat(firma.emitir(ENLACE)).doesNotContain(SECRETO);
    }

    @Test
    void laMismaEntradaDaElMismoToken() {
        assertThat(firma.emitir(ENLACE)).isEqualTo(firma.emitir(ENLACE));
    }

    /** Cambiar el barrio, la suscripción o el vencimiento conservando la firma es justo lo que la firma impide. */
    @Test
    void unTokenManipuladoNoDebeVerificarse() {
        String token = firma.emitir(ENLACE);
        String firmaAjena = token.substring(token.indexOf('.') + 1);
        String otroBarrio = firma.emitir(new EnlaceDeRestablecimiento(
                new SectorId("bocagrande"), ENLACE.suscripcion(), ENLACE.venceEn()));
        String payloadAjeno = otroBarrio.substring(0, otroBarrio.indexOf('.'));

        assertThat(firma.verificar(payloadAjeno + "." + firmaAjena)).isEmpty();
    }

    @Test
    void unTokenFirmadoConOtroSecretoNoDebeVerificarse() {
        String ajeno = new HmacFirmaDeEnlacesAdapter(nombre -> "otro-secreto-completamente-distinto-9876543210")
                .emitir(ENLACE);

        assertThat(firma.verificar(ajeno)).isEmpty();
    }

    @Test
    void lasEntradasMalFormadasNoDebenVerificarse() {
        for (String basura : new String[]{null, "", " ", "sin-punto", "a.b.c", ".firma", "payload.", "!!!.???"}) {
            assertThat(firma.verificar(basura)).as("«%s»", basura).isEmpty();
        }
    }

    /** El secreto es propio de este uso: el de los dispositivos no debe poder firmar enlaces ni al revés. */
    @Test
    void debeUsarUnSecretoPropioDeEstosEnlaces() {
        java.util.List<String> pedidos = new java.util.ArrayList<>();
        new HmacFirmaDeEnlacesAdapter(nombre -> {
            pedidos.add(nombre);
            return SECRETO;
        }).emitir(ENLACE);

        assertThat(pedidos).containsOnly(HmacFirmaDeEnlacesAdapter.SECRETO);
        assertThat(HmacFirmaDeEnlacesAdapter.SECRETO).isNotEqualTo(HmacFirmaDeDispositivosAdapter.SECRETO);
    }
}
