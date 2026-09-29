package com.aguavigia.ctg.infrastructure.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorDeNombresTest {

    @ParameterizedTest
    @CsvSource({
            "Bocagrande, bocagrande",
            "BOCAGRANDE, bocagrande",
            "Nariño, narino",
            "Andalucía, andalucia",
            "sector Sena, sena",
            "Urbanización La Heroica, la heroica",
            "conjunto Terraza de La Plazuela, terraza de la plazuela",
            "parque residencial Los Alpes, los alpes",
            "barrio   Manga, manga",
            "9 de Abril, nueve de abril",
            "NUEVE DE ABRIL, nueve de abril",
            "7 de Agosto, siete de agosto",
            "OLAYA ST. CENTRAL, olaya central",
    })
    void debeLlevarElNombreALaFormaComparable(String entrada, String esperado) {
        assertThat(NormalizadorDeNombres.normalizar(entrada)).isEqualTo(esperado);
    }

    @Test
    void unTextoNuloOEnBlancoDebeDarVacio() {
        assertThat(NormalizadorDeNombres.normalizar(null)).isEmpty();
        assertThat(NormalizadorDeNombres.normalizar("   ")).isEmpty();
    }

    @Test
    void elBoletinYElCatastroDebenCoincidirEnLosNombresConNumero() {
        assertThat(NormalizadorDeNombres.normalizar("9 de Abril"))
                .isEqualTo(NormalizadorDeNombres.normalizar("NUEVE DE ABRIL"));
    }

    /** Ante la duda no se adivina: dos barrios parecidos siguen siendo dos (ver el javadoc de la clase). */
    @Test
    void nuncaDebeConfundirBarriosParecidos() {
        assertThat(NormalizadorDeNombres.normalizar("Las Gavias"))
                .isNotEqualTo(NormalizadorDeNombres.normalizar("LAS GAVIOTAS"));
        assertThat(NormalizadorDeNombres.normalizar("Andalucía"))
                .isNotEqualTo(NormalizadorDeNombres.normalizar("SANTA LUCIA"));
    }

    @Test
    void piedraBolivarDebeEncontrarAPiedraDeBolivarPorLasVariantes() {
        assertThat(NormalizadorDeNombres.variantes("Piedra Bolívar"))
                .contains("piedra bolivar", "piedra de bolivar");
        assertThat(NormalizadorDeNombres.variantes("PIEDRA DE BOLIVAR"))
                .contains("piedra de bolivar", "piedra bolivar");
    }

    @Test
    void unNombreDeMasDeDosPalabrasNoDebeGenerarLaVarianteConDeInsertado() {
        assertThat(NormalizadorDeNombres.variantes("Ciudad Bicentenario Norte"))
                .containsExactly("ciudad bicentenario norte");
    }

    @Test
    void sinTextoNoDebeHaberVariantes() {
        assertThat(NormalizadorDeNombres.variantes("")).isEmpty();
        assertThat(NormalizadorDeNombres.variantes(null)).isEmpty();
    }
}
