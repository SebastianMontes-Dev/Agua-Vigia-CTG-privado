package com.aguavigia.ctg.architecture;

import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.EventoId;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * CLAUDE.md — Regla de Oro: si domain/ importa algo que empiece por org.springframework o
 * com.mongodb, la arquitectura está rota. Esta build lo verifica, no un acuerdo verbal.
 */
class ReglaDeOroArchitectureTest {

    private static final JavaClasses CLASES = new ClassFileImporter().importPackages("com.aguavigia.ctg");

    /** Solo producción — construir objetos de dominio a mano en un test es un patrón normal de
     * fixture, no una violación del Factory Method (que existe para proteger invariantes de
     * negocio, no para restringir cómo se arman datos de prueba). */
    private static final JavaClasses CLASES_PRODUCCION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.aguavigia.ctg");

    /**
     * Funcionalidades de la reducción por paquetes (docs/reduccion, ADR-098), sin contar {@code compartido}. Hoy están
     * vacías; se llenan fase a fase, de R1 a R9.
     */
    private static final List<String> FUNCIONALIDADES_NUEVAS = List.of(
            "com.aguavigia.ctg.sectores..", "com.aguavigia.ctg.cortes..", "com.aguavigia.ctg.reportes..",
            "com.aguavigia.ctg.bitacora..", "com.aguavigia.ctg.cumplimiento..", "com.aguavigia.ctg.estadisticas..",
            "com.aguavigia.ctg.ingesta..", "com.aguavigia.ctg.suscripciones..", "com.aguavigia.ctg.cuentas..",
            "com.aguavigia.ctg.sistema..");

    /** Las funcionalidades más {@code compartido}: lo que el código viejo ya puede usar de lo que se movió. */
    private static final List<String> PAQUETES_NUEVOS = Stream.concat(
            Stream.of("com.aguavigia.ctg.compartido.."), FUNCIONALIDADES_NUEVAS.stream()).toList();

    private static String[] permitidos(String... propios) {
        return Stream.concat(Stream.of(propios), PAQUETES_NUEVOS.stream()).toArray(String[]::new);
    }

    @Test
    void dominioNoDebeImportarSpring() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");

        regla.check(CLASES);
    }

    @Test
    void dominioNoDebeImportarMongoDB() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("com.mongodb..");

        regla.check(CLASES);
    }

    /**
     * Las dos reglas de arriba solo vetan Spring y MongoDB por nombre; esta cierra el resto —
     * cualquier framework o librería que se cuele en domain/, aunque nadie haya pensado todavía en
     * escribir una regla específica para ella (Jackson, Lombok, un validador, lo que sea). Solo
     * producción: los tests de dominio sí usan JUnit y AssertJ, legítimamente.
     */
    @Test
    void dominioNoDebeDependerDeNadaQueNoSeaJavaODominioMismo() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                // transición de la reducción (docs/reduccion): se retira en R9
                .resideOutsideOfPackages(permitidos("com.aguavigia.ctg.domain..", "java..", "javax.."));

        regla.check(CLASES_PRODUCCION);
    }

    @Test
    void applicationNoDebeDependerDeInfrastructure() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");

        regla.check(CLASES);
    }

    /**
     * CalcularEstadisticasService inyectaba MongoTemplate directamente: ni domain ni
     * infrastructure lo detectaban, porque org.springframework.data.mongodb no es
     * ..infrastructure.. ni el paquete propio del proyecto. La capa de aplicación no debe conocer
     * el motor de persistencia, solo los puertos de domain/port/out.
     */
    @Test
    void applicationNoDebeDependerDeSpringDataNiDeMongoDB() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data..", "com.mongodb..");

        regla.check(CLASES);
    }

    /**
     * Plan de validación del backend, Fase 4: application/ solo depende de dominio, de sus propios
     * casos de uso, de Java y del logging (slf4j). El cableado —detección de beans, listeners de
     * eventos, ejecución asíncrona, lectura de configuración— vive en infrastructure/
     * (`CasosDeUsoConfig`, `infrastructure/eventos`).
     */
    @Test
    void applicationSoloDebeDependerDeDominioJavaYLogging() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                // transición de la reducción (docs/reduccion): se retira en R9
                .resideOutsideOfPackages(permitidos("com.aguavigia.ctg.domain..", "com.aguavigia.ctg.application..",
                        "java..", "javax..", "org.slf4j.."));

        regla.check(CLASES_PRODUCCION);
    }

    /**
     * Plan de validación del backend, Fase 4: escuchar eventos y ejecutar en segundo plano es
     * infraestructura (`infrastructure/eventos`, `infrastructure/sse`), no cosa de un controlador.
     */
    @Test
    void apiNoDebeEscucharEventosNiProgramarTareas() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.context.event..", "org.springframework.scheduling..");

        regla.check(CLASES_PRODUCCION);
    }

    /**
     * RF026 — EventoBitacora solo se crea de negocio vía EventoBitacoraFactory; la única excepción
     * es EventoBitacoraMongoAdapter, que rehidrata eventos ya existentes desde Mongo, no crea
     * eventos nuevos (ver Javadoc de EventoBitacora).
     */
    @Test
    void eventoBitacoraSoloDebeCrearseDesdeLaFactoryODesdeElAdaptadorMongo() {
        ArchRule regla = noClasses()
                .that().resideOutsideOfPackages("com.aguavigia.ctg.domain",
                        "com.aguavigia.ctg.infrastructure.persistence.mongo")
                .should().callConstructor(EventoBitacora.class,
                        EventoId.class, TipoEvento.class, SectorId.class, CorteId.class, Instant.class, String.class);

        regla.check(CLASES_PRODUCCION);
    }

    /**
     * ADR-087: {@code RecalcularSectorService} es el único escritor del estado de un barrio, con compare-and-set
     * (`publicarSiEs`, `abrirDisputaSiEs`, `confirmarEstado`). `SectorRepository.guardar` reemplaza el documento entero
     * (estado y marcas incluidos) y `cambiarEstadoSiEs` cambia el estado sin sus marcas ni su bitácora: solo las
     * pruebas los usan para sembrar un barrio. Que nadie en producción los llame es lo que hace cierto lo del
     * «único escritor».
     */
    @Test
    void nadaEnProduccionDebeEscribirElEstadoDeUnBarrioSaltandoseElUnicoEscritor() {
        ArchRule regla = noClasses()
                .should().callMethod(SectorRepository.class, "guardar", Sector.class)
                .orShould().callMethod(SectorRepository.class, "cambiarEstadoSiEs",
                        SectorId.class, EstadoServicio.class, EstadoServicio.class);

        regla.check(CLASES_PRODUCCION);
    }

    /**
     * Los controladores son un adaptador de entrada y solo conocen los puertos y los tipos del dominio: si importan una
     * clase de infraestructura (un repositorio de Spring Data, un registro en memoria, el difusor SSE), la capa de API
     * queda atada a la tecnología y no se puede probar ni cambiar sin arrastrar el resto. Lo que necesitan lo declara
     * un puerto de salida en domain/port/out y lo implementa infraestructura (ADR-015).
     */
    @Test
    void apiNoDebeDependerDeInfrastructure() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");

        regla.check(CLASES_PRODUCCION);
    }

    /** Rutas del panel que no llevan @PreAuthorize a propósito: el acceso, la salida y quién soy. */
    private static final Set<String> RUTAS_DEL_PANEL_SIN_PERMISO = Set.of(
            "/api/veedor/sesion",          // iniciar sesión: nadie la tiene todavía
            "/api/veedor/sesion/cierre",   // cerrar la propia sesión
            "/api/veedor/yo");             // quién soy, para cualquier sesión válida

    /**
     * RNF022 — toda ruta bajo /api/veedor/** exige un permiso concreto. SecurityConfig ya pide una sesión válida para
     * todo el prefijo, pero eso solo dice quién eres; qué puedes hacer lo dice @PreAuthorize. Un endpoint nuevo del panel
     * sin él quedaría abierto a cualquier cuenta con sesión, incluida una OBSERVADORA.
     */
    @Test
    void todaRutaDelPanelDebeExigirUnPermiso() {
        DescribedPredicate<JavaMethod> atiendeRutaDelPanel = new DescribedPredicate<>("atienden una ruta de /api/veedor/**") {
            @Override
            public boolean test(JavaMethod metodo) {
                return rutasDe(metodo).stream().anyMatch(ruta -> ruta.startsWith("/api/veedor")
                        && !RUTAS_DEL_PANEL_SIN_PERMISO.contains(ruta));
            }
        };
        ArchCondition<JavaMethod> llevaPreAuthorize = new ArchCondition<>("llevar @PreAuthorize en el método o en su clase") {
            @Override
            public void check(JavaMethod metodo, ConditionEvents eventos) {
                boolean lleva = metodo.isAnnotatedWith(PreAuthorize.class)
                        || metodo.getOwner().isAnnotatedWith(PreAuthorize.class);
                if (!lleva) {
                    eventos.add(SimpleConditionEvent.violated(metodo, metodo.getFullName()
                            + " atiende " + rutasDe(metodo) + " sin @PreAuthorize"));
                }
            }
        };

        ArchRule regla = methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and(atiendeRutaDelPanel)
                .should(llevaPreAuthorize);

        regla.check(CLASES_PRODUCCION);
    }

    /** Rutas del vecino sin permiso a propósito: solo el ingreso, que nadie puede tener antes de hacerlo. */
    private static final Set<String> RUTAS_DEL_VECINO_SIN_PERMISO = Set.of("/api/vecino/sesion");

    /**
     * Igual que el panel: SecurityConfig pide sesión para todo /api/vecino/**, pero qué puede hacer esa sesión lo dice
     * @PreAuthorize. Sin él, una sesión de cualquier otro rol (un OBSERVADOR, por ejemplo) llegaría a estas rutas.
     */
    @Test
    void todaRutaDelVecinoDebeExigirUnPermiso() {
        DescribedPredicate<JavaMethod> atiendeRutaDelVecino = new DescribedPredicate<>("atienden una ruta de /api/vecino/**") {
            @Override
            public boolean test(JavaMethod metodo) {
                return rutasDe(metodo).stream().anyMatch(ruta -> ruta.startsWith("/api/vecino")
                        && !RUTAS_DEL_VECINO_SIN_PERMISO.contains(ruta));
            }
        };
        ArchCondition<JavaMethod> llevaPreAuthorize = new ArchCondition<>("llevar @PreAuthorize en el método o en su clase") {
            @Override
            public void check(JavaMethod metodo, ConditionEvents eventos) {
                boolean lleva = metodo.isAnnotatedWith(PreAuthorize.class)
                        || metodo.getOwner().isAnnotatedWith(PreAuthorize.class);
                if (!lleva) {
                    eventos.add(SimpleConditionEvent.violated(metodo, metodo.getFullName()
                            + " atiende " + rutasDe(metodo) + " sin @PreAuthorize"));
                }
            }
        };

        ArchRule regla = methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and(atiendeRutaDelVecino)
                .should(llevaPreAuthorize);

        regla.check(CLASES_PRODUCCION);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Reglas finales de la reducción (docs/reduccion/invariantes.md §5). Se añaden en R0 con allowEmptyShould porque
    // los paquetes nuevos están vacíos; así cada fase se valida contra ellas desde el primer día.
    // ---------------------------------------------------------------------------------------------------------

    /** Las reglas de negocio de cada funcionalidad (`<funcionalidad>/reglas/`) son Java puro. */
    @Test
    void reglasNoImportanFramework() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..reglas..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "com.mongodb..")
                .allowEmptyShould(true);

        regla.check(CLASES_PRODUCCION);
    }

    /** Un controlador habla con servicios; la persistencia queda detrás de ellos. */
    @Test
    void controladoresNoTocanAlmacenes() {
        ArchRule regla = noClasses()
                .that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Almacen")
                .orShould().dependOnClassesThat().areAssignableTo(MongoRepository.class)
                .allowEmptyShould(true);

        regla.check(CLASES_PRODUCCION);
    }

    /** {@code compartido} es la base de todas; si dependiera de una funcionalidad, el sentido de las flechas se rompe. */
    @Test
    void compartidoNoDependeDeFuncionalidades() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("com.aguavigia.ctg.compartido..")
                .should().dependOnClassesThat().resideInAnyPackage(FUNCIONALIDADES_NUEVAS.toArray(String[]::new))
                .allowEmptyShould(true);

        regla.check(CLASES_PRODUCCION);
    }

    /**
     * Solo {@code bitacora} construye {@code EventoBitacora}. Apunta al constructor de la clase ya movida a
     * {@code bitacora}; mientras siga en {@code domain} la regla no encuentra nada que vetar (la vigente es
     * {@link #eventoBitacoraSoloDebeCrearseDesdeLaFactoryODesdeElAdaptadorMongo}).
     */
    @Test
    void soloBitacoraCreaEventos() {
        DescribedPredicate<JavaConstructorCall> aEventoDeBitacora =
                new DescribedPredicate<>("constructores de com.aguavigia.ctg.bitacora.EventoBitacora") {
                    @Override
                    public boolean test(JavaConstructorCall llamada) {
                        return llamada.getTargetOwner().getFullName().equals("com.aguavigia.ctg.bitacora.EventoBitacora");
                    }
                };
        ArchRule regla = noClasses()
                .that().resideOutsideOfPackage("com.aguavigia.ctg.bitacora..")
                .should().callConstructorWhere(aEventoDeBitacora)
                .allowEmptyShould(true);

        regla.check(CLASES_PRODUCCION);
    }

    /** Las rutas completas (la de la clase más la del método) que atiende un método de controlador. */
    private static List<String> rutasDe(JavaMethod metodo) {
        Method reflejado = metodo.reflect();
        RequestMapping delMetodo = AnnotatedElementUtils.findMergedAnnotation(reflejado, RequestMapping.class);
        if (delMetodo == null) {
            return List.of();
        }
        RequestMapping delaClase = AnnotatedElementUtils.findMergedAnnotation(reflejado.getDeclaringClass(), RequestMapping.class);
        List<String> bases = delaClase == null || delaClase.path().length == 0 ? List.of("") : List.of(delaClase.path());
        List<String> propias = delMetodo.path().length == 0 ? List.of("") : List.of(delMetodo.path());
        return bases.stream()
                .flatMap(base -> propias.stream().map(propia -> (base + propia).replaceAll("/+$", "")))
                .toList();
    }
}
