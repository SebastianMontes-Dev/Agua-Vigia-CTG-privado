package com.aguavigia.ctg.infrastructure.security;

import com.aguavigia.ctg.domain.DispositivoId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HmacFirmaDeDispositivosAdapterTest {

    private static final String SECRETO = "secreto-de-prueba-que-nadie-mas-conoce-0123456789";
    private static final DispositivoId ID = new DispositivoId("7f1c2b9e-4d3a-4e5b-8c6d-1a2b3c4d5e6f");

    private final HmacFirmaDeDispositivosAdapter firma = new HmacFirmaDeDispositivosAdapter(nombre -> SECRETO);

    @Test
    void unTokenEmitidoDebeVerificarseYDevolverElMismoId() {
        String token = firma.emitir(ID);

        assertThat(firma.verificar(token)).contains(ID);
    }

    @Test
    void elTokenDebeLlevarElIdYSuFirmaSeparadosPorUnPunto() {
        String token = firma.emitir(ID);

        assertThat(token).startsWith(ID.valor() + ".");
        assertThat(token.substring(ID.valor().length() + 1)).isNotBlank();
    }

    @Test
    void elTokenNoDebeContenerElSecreto() {
        assertThat(firma.emitir(ID)).doesNotContain(SECRETO);
    }

    /** La firma va en base64url sin relleno: el token viaja en una cabecera y no debe necesitar escapes. */
    @Test
    void elTokenSoloDebeUsarCaracteresSegurosParaUnaCabecera() {
        assertThat(firma.emitir(ID)).matches("[A-Za-z0-9._-]+");
    }

    @Test
    void laMismaEntradaDebeDarSiempreElMismoToken() {
        assertThat(firma.emitir(ID)).isEqualTo(firma.emitir(ID));
    }

    @Test
    void dosDispositivosDistintosDebenTenerTokensDistintos() {
        assertThat(firma.emitir(ID)).isNotEqualTo(firma.emitir(new DispositivoId("otro-dispositivo")));
    }

    /** Cambiar el id conservando la firma ajena es justo lo que la firma existe para impedir. */
    @Test
    void unTokenConElIdCambiadoNoDebeVerificarse() {
        String token = firma.emitir(ID);
        String firmaAjena = token.substring(token.indexOf('.') + 1);

        assertThat(firma.verificar("otro-dispositivo." + firmaAjena)).isEmpty();
    }

    @Test
    void unTokenConLaFirmaAlteradaNoDebeVerificarse() {
        String token = firma.emitir(ID);
        String alterado = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThat(firma.verificar(alterado)).isEmpty();
    }

    @Test
    void unTokenFirmadoConOtroSecretoNoDebeVerificarse() {
        String ajeno = new HmacFirmaDeDispositivosAdapter(nombre -> "otro-secreto-completamente-distinto-9876543210")
                .emitir(ID);

        assertThat(firma.verificar(ajeno)).isEmpty();
    }

    @Test
    void unTokenSinFirmaOMalFormadoNoDebeVerificarse() {
        assertThat(firma.verificar(null)).isEmpty();
        assertThat(firma.verificar("")).isEmpty();
        assertThat(firma.verificar("   ")).isEmpty();
        assertThat(firma.verificar(ID.valor())).isEmpty();
        assertThat(firma.verificar(ID.valor() + ".")).isEmpty();
        assertThat(firma.verificar("." + "firma")).isEmpty();
        assertThat(firma.verificar("a.b.c")).isEmpty();
        assertThat(firma.verificar("€€€.%%%")).isEmpty();
    }

    @Test
    void debePedirElSecretoPorSuNombreYNoDebeDevolverVacioConUnTokenValido() {
        java.util.List<String> pedidos = new java.util.ArrayList<>();
        HmacFirmaDeDispositivosAdapter conRegistro = new HmacFirmaDeDispositivosAdapter(nombre -> {
            pedidos.add(nombre);
            return SECRETO;
        });

        Optional<DispositivoId> verificado = conRegistro.verificar(conRegistro.emitir(ID));

        assertThat(verificado).contains(ID);
        assertThat(pedidos).isNotEmpty().allMatch("dispositivos"::equals);
    }
}
