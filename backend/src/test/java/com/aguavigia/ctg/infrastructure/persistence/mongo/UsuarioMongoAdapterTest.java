package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.Consentimiento;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.TipoConsentimiento;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SecretoTotp;
import com.aguavigia.ctg.domain.SegundoFactor;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración contra un MongoDB real (Testcontainers). Requiere Docker.
 * Cubre el adaptador de cuentas: ida y vuelta de todos los campos, el barrio (ADR-081) y el listado filtrado.
 */
@Testcontainers
@DataMongoTest
@Import(UsuarioMongoAdapter.class)
class UsuarioMongoAdapterTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");
    private static final ClaveHash HASH = new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private UsuarioMongoAdapter adaptador;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("usuarios").drop();
    }

    private static Usuario cuenta(String id, String correo, EstadoCuenta estado, SectorId barrio, Instant creadoEn) {
        return new Usuario(new UsuarioId(id), new CorreoElectronico(correo), "Persona " + id, HASH, estado,
                PermisosEfectivos.deRol(RolVeedor.OBSERVADOR), null, creadoEn, creadoEn, barrio);
    }

    @Test
    void debeGuardarYRecuperarLaCuentaConTodosSusCampos() {
        SegundoFactor totp = new SegundoFactor(new SecretoTotp("GEZDGNBVGY3TQOJQ"), T0.plusSeconds(60));
        Usuario original = new Usuario(new UsuarioId("u-1"), new CorreoElectronico("Ana@Ejemplo.org"), "Ana", HASH,
                EstadoCuenta.ACTIVA, PermisosEfectivos.deRol(RolVeedor.ADMIN), totp, T0, T0.plusSeconds(120),
                new SectorId("manga"));

        adaptador.guardar(original);
        Usuario leido = adaptador.buscarPorId(new UsuarioId("u-1")).orElseThrow();

        assertThat(leido.correo().valor()).isEqualTo("ana@ejemplo.org");
        assertThat(leido.nombre()).isEqualTo("Ana");
        assertThat(leido.claveHash()).isEqualTo(HASH);
        assertThat(leido.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(leido.permisos().rol()).isEqualTo(RolVeedor.ADMIN);
        assertThat(leido.segundoFactor().secreto().valor()).isEqualTo("GEZDGNBVGY3TQOJQ");
        assertThat(leido.segundoFactor().confirmadoEn()).isEqualTo(T0.plusSeconds(60));
        assertThat(leido.barrio()).isEqualTo(new SectorId("manga"));
        assertThat(leido.creadoEn()).isEqualTo(T0);
    }

    @Test
    void debeGuardarYRecuperarLaCuentaDeVecinoConConsentimientosYBarrioVerificado() {
        Usuario vecino = Usuario.registradoComoVecino(new UsuarioId("v-1"), new CorreoElectronico("vecina@ejemplo.org"),
                        "Vecina", new SectorId("manga"),
                        List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "2026-10-v1", T0),
                                new Consentimiento(TipoConsentimiento.AVISOS, "2026-10-v1", T0)), T0)
                .aceptarInvitacion(HASH, T0.plusSeconds(30))
                .verificarBarrio(T0.plusSeconds(60));

        adaptador.guardar(vecino);
        Usuario leido = adaptador.buscarPorId(new UsuarioId("v-1")).orElseThrow();

        assertThat(leido.permisos().rol()).isEqualTo(RolVeedor.VECINO);
        assertThat(leido.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(leido.barrio()).isEqualTo(new SectorId("manga"));
        assertThat(leido.barrioVerificado()).isTrue();
        assertThat(leido.barrioVerificadoEn()).isEqualTo(T0.plusSeconds(60));
        // Lo aceptado vale desde que la persona del correo actua (al fijar su clave), no desde el formulario.
        assertThat(leido.consentimientos()).containsExactly(
                new Consentimiento(TipoConsentimiento.PRIVACIDAD, "2026-10-v1", T0.plusSeconds(30)),
                new Consentimiento(TipoConsentimiento.AVISOS, "2026-10-v1", T0.plusSeconds(30)));
        assertThat(leido.recibeAvisos()).isTrue();
    }

    /** Las cuentas guardadas antes de los vecinos (como el ADMIN inicial) no traen estos campos y deben seguir legibles. */
    @Test
    void unDocumentoAnteriorALosVecinosDebeLeerseSinConsentimientosNiBarrioVerificado() {
        mongoTemplate.getDb().getCollection("usuarios").insertOne(new org.bson.Document()
                .append("_id", "u-viejo")
                .append("correo", "viejo@ejemplo.org")
                .append("nombre", "Cuenta anterior")
                .append("claveHash", HASH.valor())
                .append("estado", "ACTIVA")
                .append("rol", "VEEDOR")
                .append("creadoEn", java.util.Date.from(T0))
                .append("actualizadoEn", java.util.Date.from(T0)));

        Usuario leido = adaptador.buscarPorId(new UsuarioId("u-viejo")).orElseThrow();

        assertThat(leido.consentimientos()).isEmpty();
        assertThat(leido.barrioVerificado()).isFalse();
        assertThat(leido.barrioVerificadoEn()).isNull();
    }

    @Test
    void unaCuentaSinBarrioDebeVolverSinBarrio() {
        adaptador.guardar(cuenta("u-2", "sin-barrio@ejemplo.org", EstadoCuenta.ACTIVA, null, T0));

        assertThat(adaptador.buscarPorId(new UsuarioId("u-2")).orElseThrow().barrio()).isNull();
    }

    @Test
    void debeBuscarPorCorreoSinImportarLasMayusculas() {
        adaptador.guardar(cuenta("u-3", "beto@ejemplo.org", EstadoCuenta.ACTIVA, null, T0));

        assertThat(adaptador.buscarPorCorreo(new CorreoElectronico("BETO@ejemplo.org"))).isPresent();
        assertThat(adaptador.existePorCorreo(new CorreoElectronico("BETO@ejemplo.org"))).isTrue();
        assertThat(adaptador.existePorCorreo(new CorreoElectronico("otro@ejemplo.org"))).isFalse();
    }

    @Test
    void elListadoDebeFiltrarPorEstadoYPorBarrioYOrdenarPorLasMasRecientes() {
        SectorId manga = new SectorId("manga");
        SectorId crespo = new SectorId("crespo");
        adaptador.guardar(cuenta("a", "a@ejemplo.org", EstadoCuenta.ACTIVA, manga, T0));
        adaptador.guardar(cuenta("b", "b@ejemplo.org", EstadoCuenta.ACTIVA, manga, T0.plusSeconds(10)));
        adaptador.guardar(cuenta("c", "c@ejemplo.org", EstadoCuenta.SUSPENDIDA, manga, T0.plusSeconds(20)));
        adaptador.guardar(cuenta("d", "d@ejemplo.org", EstadoCuenta.ACTIVA, crespo, T0.plusSeconds(30)));
        adaptador.guardar(cuenta("e", "e@ejemplo.org", EstadoCuenta.ACTIVA, null, T0.plusSeconds(40)));

        assertThat(ids(adaptador.listar(null, null, 0, 20))).containsExactly("e", "d", "c", "b", "a");
        assertThat(ids(adaptador.listar(EstadoCuenta.ACTIVA, null, 0, 20))).containsExactly("e", "d", "b", "a");
        assertThat(ids(adaptador.listar(null, manga, 0, 20))).containsExactly("c", "b", "a");
        assertThat(ids(adaptador.listar(EstadoCuenta.ACTIVA, manga, 0, 20))).containsExactly("b", "a");
        assertThat(adaptador.listar(null, manga, 0, 2).totalElementos()).isEqualTo(3);
        assertThat(ids(adaptador.listar(null, manga, 1, 2))).containsExactly("a");
    }

    @Test
    void debeContarSoloLasCuentasActivasDeUnRol() {
        adaptador.guardar(cuenta("a", "a@ejemplo.org", EstadoCuenta.ACTIVA, null, T0));
        adaptador.guardar(cuenta("b", "b@ejemplo.org", EstadoCuenta.SUSPENDIDA, null, T0));

        assertThat(adaptador.contarActivosPorRol(RolVeedor.OBSERVADOR)).isEqualTo(1);
        assertThat(adaptador.contarActivosPorRol(RolVeedor.ADMIN)).isZero();
    }

    private static List<String> ids(Pagina<Usuario> pagina) {
        return pagina.contenido().stream().map(u -> u.id().valor()).toList();
    }

    // --- cuentas sintéticas (D20, D36) ---

    private static Usuario sintetica(int numero) {
        return Usuario.sinteticoComoVecino(new UsuarioId("s-" + numero),
                new CorreoElectronico("cuenta-sintetica-%06d@demo.aguavigia.invalid".formatted(numero)),
                "Cuenta sintética " + numero, new SectorId("manga"), T0).aceptarInvitacion(HASH, T0);
    }

    @Test
    void laMarcaDeDemostracionDaLaVueltaYSeEscribeComoOrigenSembrado() {
        adaptador.guardar(sintetica(1));
        adaptador.guardar(cuenta("real", "ana@correo.com", EstadoCuenta.ACTIVA, new SectorId("manga"), T0));

        assertThat(adaptador.buscarPorId(new UsuarioId("s-1")).orElseThrow().datosDeDemostracion()).isTrue();
        assertThat(adaptador.buscarPorId(new UsuarioId("real")).orElseThrow().datosDeDemostracion()).isFalse();
        org.bson.Document crudo = mongoTemplate.getDb().getCollection("usuarios").find(new org.bson.Document("_id", "s-1")).first();
        assertThat(crudo.getString("origen")).isEqualTo("SEMBRADO");
        assertThat(crudo.getBoolean("datosDeDemostracion")).isTrue();
        org.bson.Document real = mongoTemplate.getDb().getCollection("usuarios").find(new org.bson.Document("_id", "real")).first();
        assertThat(real.get("origen")).isNull();
    }

    @Test
    void cuentaSoloLasSinteticas() {
        adaptador.guardar(sintetica(1));
        adaptador.guardar(sintetica(2));
        adaptador.guardar(cuenta("real", "ana@correo.com", EstadoCuenta.ACTIVA, new SectorId("manga"), T0));

        assertThat(adaptador.contarSinteticas()).isEqualTo(2);
    }

    @Test
    void insertaElLoteYAlRepetirloNoDuplicaNiFalla() {
        java.util.List<Usuario> lote = java.util.stream.IntStream.rangeClosed(1, 50)
                .mapToObj(UsuarioMongoAdapterTest::sintetica).toList();

        assertThat(adaptador.insertarSinteticasSiNoExisten(lote)).isEqualTo(50);
        assertThat(adaptador.insertarSinteticasSiNoExisten(lote)).as("segunda pasada: ya existen").isZero();

        java.util.List<Usuario> solapado = java.util.stream.IntStream.rangeClosed(41, 60)
                .mapToObj(UsuarioMongoAdapterTest::sintetica).toList();
        assertThat(adaptador.insertarSinteticasSiNoExisten(solapado)).as("10 ya estaban, 10 son nuevas").isEqualTo(10);
        assertThat(adaptador.contarSinteticas()).isEqualTo(60);
    }

    @Test
    void unLoteVacioNoHaceNada() {
        assertThat(adaptador.insertarSinteticasSiNoExisten(java.util.List.of())).isZero();
    }

    /**
     * Contrato con scripts/verificar-datos.mjs: ese script no usa el adaptador, lee el documento crudo y espera estos campos y
     * valores. Si el adaptador cambia cómo escribe una cuenta sintética, este test falla antes de que la verificación mienta.
     */
    @Test
    void elDocumentoSinteticoTieneLosCamposQueVerificarDatosEspera() {
        adaptador.insertarSinteticasSiNoExisten(java.util.List.of(sintetica(9)));

        org.bson.Document crudo = mongoTemplate.getDb().getCollection("usuarios").find(new org.bson.Document("_id", "s-9")).first();
        assertThat(crudo.getString("rol")).isEqualTo("VECINO");
        assertThat(crudo.getString("estado")).isEqualTo("ACTIVA");
        assertThat(crudo.getString("barrio")).isEqualTo("manga");
        assertThat(crudo.getBoolean("datosDeDemostracion")).isTrue();
        assertThat(crudo.getString("origen")).isEqualTo("SEMBRADO");
        assertThat(crudo.getString("correo")).endsWith(".invalid");
        // Nada fingido: ni barrio verificado ni consentimientos que nadie dio.
        assertThat(crudo.getBoolean("barrioVerificado", false)).isFalse();
        assertThat(crudo.getList("consentimientos", org.bson.Document.class, java.util.List.of())).isEmpty();
    }

    /** Una cuenta sintética leída de la base sigue siendo una cuenta válida: activa, con clave, sin consentimientos fingidos. */
    @Test
    void laCuentaSinteticaLeidaDeLaBaseEsValida() {
        adaptador.insertarSinteticasSiNoExisten(java.util.List.of(sintetica(7)));

        Usuario leida = adaptador.buscarPorId(new UsuarioId("s-7")).orElseThrow();
        assertThat(leida.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(leida.esVecino()).isTrue();
        assertThat(leida.claveHash()).isEqualTo(HASH);
        assertThat(leida.consentimientos()).isEmpty();
        assertThat(leida.barrioVerificado()).isFalse();
        assertThat(adaptador.buscarPorCorreo(new CorreoElectronico("cuenta-sintetica-000007@demo.aguavigia.invalid"))).isPresent();
    }
}
