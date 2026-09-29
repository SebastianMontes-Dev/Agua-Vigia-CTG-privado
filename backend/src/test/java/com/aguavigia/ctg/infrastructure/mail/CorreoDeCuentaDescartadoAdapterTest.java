package com.aguavigia.ctg.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CorreoDeCuentaDescartadoAdapterTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withPropertyValues("spring.mail.host=localhost")
            .withUserConfiguration(MailCuentaAdapter.class, CorreoDeCuentaDescartadoAdapter.class);

    @Test
    void porDefectoDebenSalirLosCorreosDeCuentas() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(MailCuentaAdapter.class);
            assertThat(ctx).doesNotHaveBean(CorreoDeCuentaDescartadoAdapter.class);
        });
    }

    @Test
    void conLosCorreosDeCuentasApagadosDebeDescartarlosSinFallar() {
        contexto.withPropertyValues("aguavigia.correo.cuentas-habilitado=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(MailCuentaAdapter.class);
            CorreoDeCuentaDescartadoAdapter adaptador = ctx.getBean(CorreoDeCuentaDescartadoAdapter.class);

            assertThatCode(() -> {
                adaptador.enviarVerificacionDeCorreo(null, "token");
                adaptador.enviarEnlaceDeRestablecimiento(null, "token");
                adaptador.avisarCambioDeAcceso(null, "asunto", "mensaje");
            }).doesNotThrowAnyException();
            assertThat(adaptador.descartados()).isEqualTo(3);
        });
    }
}
