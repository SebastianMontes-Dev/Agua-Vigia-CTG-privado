package com.aguavigia.ctg.architecture;

import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.EventoId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;

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
                .resideOutsideOfPackages("com.aguavigia.ctg.domain..", "java..", "javax..");

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
                .resideOutsideOfPackages("com.aguavigia.ctg.domain..", "com.aguavigia.ctg.application..",
                        "java..", "javax..", "org.slf4j..");

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
