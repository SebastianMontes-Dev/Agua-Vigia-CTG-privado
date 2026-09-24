package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaginaDeListaTest {

    private static final List<Integer> DIEZ = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    @Test
    void debeCortarElTramoPedido() {
        Pagina<Integer> pagina = Pagina.deLista(DIEZ, 1, 4);

        assertThat(pagina.contenido()).containsExactly(5, 6, 7, 8);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamano()).isEqualTo(4);
        assertThat(pagina.totalElementos()).isEqualTo(10);
    }

    @Test
    void laUltimaPaginaPuedeVenirIncompleta() {
        assertThat(Pagina.deLista(DIEZ, 2, 4).contenido()).containsExactly(9, 10);
    }

    @Test
    void unaPaginaMasAllaDelFinalDebeVenirVaciaSinFallar() {
        Pagina<Integer> pagina = Pagina.deLista(DIEZ, 50, 4);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isEqualTo(10);
    }

    @Test
    void unaListaVaciaDebeDarUnaPaginaVacia() {
        assertThat(Pagina.deLista(List.<Integer>of(), 0, 50).contenido()).isEmpty();
    }

    @Test
    void unNumeroDePaginaEnormeNoDebeDesbordarElCalculoDelIndice() {
        assertThat(Pagina.deLista(DIEZ, Integer.MAX_VALUE, 200).contenido()).isEmpty();
    }
}
