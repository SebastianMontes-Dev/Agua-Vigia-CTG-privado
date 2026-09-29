package com.aguavigia.ctg.infrastructure.ingest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AliasDeBarriosTest {

    private final AliasDeBarrios alias = AliasDeBarrios.cargar();

    @Test
    void unNombreQueAbarcaVariosSectoresDebeDevolverTodosSusSlugs() {
        assertThat(alias.slugsPara("Olaya Herrera"))
                .contains("olaya-st-rafael-nunez", "olaya-villa-olimpica")
                .hasSizeGreaterThan(2);
    }

    @Test
    void debeResolverSinImportarMayusculasNiAcentosNiElPrefijoDeTipo() {
        assertThat(alias.slugsPara("OLAYA HERRERA")).isEqualTo(alias.slugsPara("Olaya Herrera"));
        assertThat(alias.slugsPara("sector Olaya Herrera")).isEqualTo(alias.slugsPara("Olaya Herrera"));
    }

    @Test
    void unNombreSinEquivalenciaCuradaDebeDarListaVacia() {
        assertThat(alias.slugsPara("Barrio Que No Existe")).isEmpty();
        assertThat(alias.slugsPara(null)).isEmpty();
    }

    @Test
    void losNombresDeclaradosDebenEstarNormalizados() {
        assertThat(alias.nombresDeclarados()).contains("olaya herrera");
        assertThat(alias.nombresDeclarados()).allSatisfy(nombre -> assertThat(nombre)
                .isEqualTo(NormalizadorDeNombres.normalizar(nombre)));
    }
}
