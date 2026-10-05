package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ModoDelSistema;
import com.aguavigia.ctg.domain.ModoDelSistema.Modo;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConsultarModoDelSistemaServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-02T08:00:00Z"));

    @Test
    void declaraElModoYCuantasCuentasSonSinteticas() {
        given(usuarios.contarSinteticas()).willReturn(30_000L);

        ModoDelSistema modo = new ConsultarModoDelSistemaService(usuarios, Modo.REAL, ahora::get).consultar();

        assertThat(modo.modo()).isEqualTo(Modo.REAL);
        assertThat(modo.cuentasSinteticas()).isEqualTo(30_000);
    }

    @Test
    void laSimulacionSeDeclaraComoTal() {
        assertThat(new ConsultarModoDelSistemaService(usuarios, Modo.SIMULACION, ahora::get).consultar().modo())
                .isEqualTo(Modo.SIMULACION);
    }

    /** Es público y lo pide cada pantalla: contar 30 000 documentos en cada petición sería un coste que cualquiera podría disparar. */
    @Test
    void elConteoSeRecuerdaUnMinuto() {
        given(usuarios.contarSinteticas()).willReturn(10L, 20L);
        var servicio = new ConsultarModoDelSistemaService(usuarios, Modo.REAL, ahora::get);

        assertThat(servicio.consultar().cuentasSinteticas()).isEqualTo(10);
        ahora.set(ahora.get().plus(Duration.ofSeconds(30)));
        assertThat(servicio.consultar().cuentasSinteticas()).isEqualTo(10);
        verify(usuarios, times(1)).contarSinteticas();

        ahora.set(ahora.get().plus(Duration.ofSeconds(31)));
        assertThat(servicio.consultar().cuentasSinteticas()).isEqualTo(20);
    }

    /** Un Mongo caído no puede esconder el modo: el banner de la simulación sigue saliendo, con el último conteo que se conocía. */
    @Test
    void siMongoFallaDevuelveElUltimoConteoConocido() {
        given(usuarios.contarSinteticas()).willReturn(10L).willThrow(new IllegalStateException("mongo caído"));
        var servicio = new ConsultarModoDelSistemaService(usuarios, Modo.SIMULACION, ahora::get);
        servicio.consultar();

        ahora.set(ahora.get().plus(Duration.ofMinutes(2)));
        ModoDelSistema modo = servicio.consultar();

        assertThat(modo.modo()).isEqualTo(Modo.SIMULACION);
        assertThat(modo.cuentasSinteticas()).isEqualTo(10);
    }

    @Test
    void siMongoFallaYNuncaSeContoDeclaraElModoSinCuentasEnVezDeFallar() {
        given(usuarios.contarSinteticas()).willThrow(new IllegalStateException("mongo caído"));

        ModoDelSistema modo = new ConsultarModoDelSistemaService(usuarios, Modo.SIMULACION, ahora::get).consultar();

        assertThat(modo.modo()).isEqualTo(Modo.SIMULACION);
        assertThat(modo.cuentasSinteticas()).isZero();
    }

    /** Un Mongo caído no se reintenta en cada visita: el intento fallido también ocupa su minuto. */
    @Test
    void unConteoFallidoNoSeReintentaEnCadaPeticion() {
        given(usuarios.contarSinteticas()).willThrow(new IllegalStateException("mongo caído"));
        var servicio = new ConsultarModoDelSistemaService(usuarios, Modo.REAL, ahora::get);

        servicio.consultar();
        servicio.consultar();
        servicio.consultar();

        verify(usuarios, times(1)).contarSinteticas();
    }

    /** Mientras alguien cuenta, los demás no esperan en fila detrás de una consulta a Mongo: reciben el valor anterior. */
    @Test
    void mientrasOtroHiloCuentaLosDemasNoEsperan() throws Exception {
        var contando = new java.util.concurrent.CountDownLatch(1);
        var soltar = new java.util.concurrent.CountDownLatch(1);
        given(usuarios.contarSinteticas()).willReturn(10L).willAnswer(invocacion -> {
            contando.countDown();
            soltar.await(10, java.util.concurrent.TimeUnit.SECONDS);
            return 20L;
        });
        var servicio = new ConsultarModoDelSistemaService(usuarios, Modo.REAL, ahora::get);
        servicio.consultar();
        ahora.set(ahora.get().plus(Duration.ofMinutes(2)));

        var ejecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var recuento = ejecutor.submit(servicio::consultar);
            assertThat(contando.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            long cuentas = java.util.concurrent.CompletableFuture.supplyAsync(() -> servicio.consultar().cuentasSinteticas())
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(cuentas).isEqualTo(10);
            soltar.countDown();
            assertThat(recuento.get(5, java.util.concurrent.TimeUnit.SECONDS).cuentasSinteticas()).isEqualTo(20);
        } finally {
            soltar.countDown();
            ejecutor.shutdownNow();
        }
    }

    @Test
    void elNombreDelModoSeLeeSinImportarMayusculas() {
        assertThat(Modo.deTexto("simulacion")).isEqualTo(Modo.SIMULACION);
        assertThat(Modo.deTexto(" REAL ")).isEqualTo(Modo.REAL);
        assertThat(Modo.deTexto(null)).isEqualTo(Modo.REAL);
    }

    @Test
    void unModoDesconocidoNoPasaEnSilencio() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Modo.deTexto("demo"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("demo");
    }
}
