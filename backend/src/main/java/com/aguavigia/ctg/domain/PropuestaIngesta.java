package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * M9 — lo que la ingesta automatizada *propone* para un sector, no lo que publica.
 *
 * Antes el pipeline llamaba directo a `SectorRepository.guardar()`, y con eso una expresión regular
 * sobre una nota de prensa cambiaba el estado público de un barrio, mandaba correo a sus
 * suscriptores y movía el mapa. Hoy toda detección nace como propuesta y quién la aprueba depende
 * de su origen: la del operador oficial se publica sola ({@link #esDeFuenteOficial}), la inferida de
 * prensa espera al veedor (`RevisarPropuestaIngestaUseCase`). El riesgo que importaba —inferir un
 * corte de un texto ajeno y publicarlo— sigue cubierto.
 *
 * `citaTextual` y `confianza` existen para que esa decisión sea informada: el veedor ve de dónde
 * salió la afirmación antes de publicarla. Es la misma exigencia de `ADR-006` (cita verificable en
 * toda extracción), que no se cayó con el descarte de la IA en `ADR-025`.
 */
public record PropuestaIngesta(
        PropuestaId id,
        SectorId sectorId,
        EstadoServicio estadoPropuesto,
        String fuente,
        String urlOriginal,
        String citaTextual,
        double confianza,
        Instant detectadaEn,
        EstadoRevision estadoRevision,
        Instant inicioDeclarado,
        Instant finPrometido,
        /** Portada del boletín, cuando la fuente la trae. Viaja hasta la bitácora pública. */
        String imagenUrl,
        /** Cuándo publicó la fuente el boletín. Es la fecha del hecho cuando no hay ventana declarada. */
        Instant publicadoEn,
        /** Titular tal como lo publicó la fuente. Es lo que la bitácora enseña al vecino. */
        String tituloOriginal,
        /** Por qué se anuló. Obligatorio si la propuesta está ANULADA, y solo entonces. */
        String motivoAnulacion,
        /**
         * Por qué esta propuesta espera al veedor en vez de publicarse sola (D5): la confianza, una ventana sin sentido,
         * demasiados barrios… Nulo si salió sola. Se conserva tras revisarla, para que la cola auditada diga por qué pasó por ahí.
         */
        String motivoDeRevision) {

    /** Sin motivo de revisión: lo que existía antes de las compuertas de publicación. */
    public PropuestaIngesta(PropuestaId id, SectorId sectorId, EstadoServicio estadoPropuesto,
                             String fuente, String urlOriginal, String citaTextual, double confianza,
                             Instant detectadaEn, EstadoRevision estadoRevision,
                             Instant inicioDeclarado, Instant finPrometido,
                             String imagenUrl, Instant publicadoEn, String tituloOriginal, String motivoAnulacion) {
        this(id, sectorId, estadoPropuesto, fuente, urlOriginal, citaTextual, confianza, detectadaEn,
                estadoRevision, inicioDeclarado, finPrometido, imagenUrl, publicadoEn, tituloOriginal,
                motivoAnulacion, null);
    }

    /** Sin motivo de anulación: lo que existía antes de poder anular. */
    public PropuestaIngesta(PropuestaId id, SectorId sectorId, EstadoServicio estadoPropuesto,
                             String fuente, String urlOriginal, String citaTextual, double confianza,
                             Instant detectadaEn, EstadoRevision estadoRevision,
                             Instant inicioDeclarado, Instant finPrometido,
                             String imagenUrl, Instant publicadoEn, String tituloOriginal) {
        this(id, sectorId, estadoPropuesto, fuente, urlOriginal, citaTextual, confianza, detectadaEn,
                estadoRevision, inicioDeclarado, finPrometido, imagenUrl, publicadoEn, tituloOriginal, null, null);
    }

    /** Sin portada: las fuentes de prensa no la traen. */
    public PropuestaIngesta(PropuestaId id, SectorId sectorId, EstadoServicio estadoPropuesto,
                             String fuente, String urlOriginal, String citaTextual, double confianza,
                             Instant detectadaEn, EstadoRevision estadoRevision,
                             Instant inicioDeclarado, Instant finPrometido) {
        this(id, sectorId, estadoPropuesto, fuente, urlOriginal, citaTextual, confianza, detectadaEn,
                estadoRevision, inicioDeclarado, finPrometido, null, null, null);
    }

    public PropuestaIngesta {
        if (inicioDeclarado != null && finPrometido != null && !finPrometido.isAfter(inicioDeclarado)) {
            throw new IllegalArgumentException(
                    "La ventana declarada debe terminar después de empezar: " + inicioDeclarado
                            + " → " + finPrometido);
        }
        if (sectorId == null) {
            throw new IllegalArgumentException("La propuesta debe apuntar a un sector");
        }
        if (estadoPropuesto == null) {
            throw new IllegalArgumentException("La propuesta debe declarar el estado que propone");
        }
        if (fuente == null || fuente.isBlank()) {
            throw new IllegalArgumentException("La propuesta debe declarar su fuente");
        }
        if (confianza < 0 || confianza > 1) {
            throw new IllegalArgumentException("La confianza debe estar entre 0 y 1: " + confianza);
        }
        if (detectadaEn == null) {
            throw new IllegalArgumentException("La propuesta debe tener fecha de detección");
        }
        if (estadoRevision == null) {
            throw new IllegalArgumentException("La propuesta debe tener un estado de revisión");
        }
        if ((estadoRevision == EstadoRevision.ANULADA) != (motivoAnulacion != null && !motivoAnulacion.isBlank())) {
            throw new IllegalArgumentException("El motivo de anulación es obligatorio si la propuesta está ANULADA, y solo entonces");
        }
    }

    /** Una propuesta recién detectada siempre nace PENDIENTE: nada entra al mapa sin revisar. */
    public PropuestaIngesta(PropuestaId id, SectorId sectorId, EstadoServicio estadoPropuesto,
                             String fuente, String urlOriginal, String citaTextual,
                             double confianza, Instant detectadaEn) {
        this(id, sectorId, estadoPropuesto, fuente, urlOriginal, citaTextual, confianza, detectadaEn,
                EstadoRevision.PENDIENTE, null, null);
    }

    /** Con la ventana que el boletín prometió, cuando el extractor logró leerla (RF020–RF022). */
    public PropuestaIngesta(PropuestaId id, SectorId sectorId, EstadoServicio estadoPropuesto,
                             String fuente, String urlOriginal, String citaTextual,
                             double confianza, Instant detectadaEn,
                             Instant inicioDeclarado, Instant finPrometido) {
        this(id, sectorId, estadoPropuesto, fuente, urlOriginal, citaTextual, confianza, detectadaEn,
                EstadoRevision.PENDIENTE, inicioDeclarado, finPrometido);
    }

    /**
     * Qué estado le corresponde al sector <b>en este instante</b> según la ventana prometida. Es lo
     * que permite que un corte anunciado para mañana se publique como CORTE_PROGRAMADO hoy, pase a
     * SIN_SERVICIO cuando empieza y vuelva a CON_SERVICIO cuando termina, sin que nadie lo toque a
     * mano. Sin ventana declarada el estado no evoluciona: se queda en el que se aprobó, porque
     * inventar el momento del cambio sería el mismo dato fabricado que `ADR-006` prohíbe.
     */
    public EstadoServicio estadoVigenteEn(Instant momento) {
        if (estadoPropuesto == EstadoServicio.CON_SERVICIO || inicioDeclarado == null) {
            return estadoPropuesto;
        }
        if (momento.isBefore(inicioDeclarado)) {
            return EstadoServicio.CORTE_PROGRAMADO;
        }
        if (finPrometido != null && !momento.isBefore(finPrometido)) {
            return EstadoServicio.CON_SERVICIO;
        }
        return estadoPropuesto;
    }

    /**
     * Acuacar no es una fuente *sobre* el corte: es quien lo ejecuta. Su boletín, con cita textual y
     * URL verificable, es el anuncio oficial, no una inferencia de una expresión regular sobre texto
     * ajeno — que era exactamente el riesgo contra el que `ADR-028` levantó la cola de revisión. Por
     * eso lo oficial se publica solo y la prensa (RSS) sigue esperando al veedor.
     */
    public boolean esDeFuenteOficial() {
        return FUENTE_OFICIAL.equalsIgnoreCase(fuente);
    }

    /**
     * Con qué fecha entra este hecho a la bitácora. La bitácora es una línea de tiempo de lo que le
     * pasó al acueducto, no un registro de cuándo corrió el colector: un boletín que anuncia un
     * corte para el 21 de agosto pertenece al 21 de agosto, aunque se procese semanas después. Sin
     * esto, recuperar el histórico de Acuacar sellaría cientos de eventos con la hora de la
     * recuperación y el orden cronológico de RF026 dejaría de decir nada.
     *
     * Se usa `inicioDeclarado` porque es el instante que el propio boletín afirma, no uno inferido.
     * Si el boletín no declara ventana, no hay fecha del hecho que citar y se cae al momento de
     * detección, que es lo único verificable que queda.
     */
    public Instant momentoParaLaBitacora(Instant ahora) {
        if (inicioDeclarado != null) {
            return inicioDeclarado;
        }
        // La fecha en que la fuente lo publicó, no la hora en que lo leímos. Sin esto, recuperar el
        // histórico fecha todo "hace un momento": un boletín del 8 de julio aparecía como de hoy.
        if (publicadoEn != null) {
            return publicadoEn;
        }
        return detectadaEn != null ? detectadaEn : ahora;
    }

    /** Cuándo se hizo pública: su publicación o, si la fuente no la trae, cuándo se detectó. Entre dos boletines, el más reciente gana. */
    public Instant momento() {
        return publicadoEn != null ? publicadoEn : detectadaEn;
    }

    /** Si la ventana que prometió es exactamente esa: lo que ata un boletín al corte que el barrio está publicando. */
    public boolean tieneLaVentanaDe(VentanaTiempo ventana) {
        return ventana != null
                && ventana.inicio().equals(inicioDeclarado)
                && ventana.finPrometido().equals(finPrometido);
    }

    /** El nombre con que el colector de Acuacar firma sus documentos. */
    public static final String FUENTE_OFICIAL = "acuacar";

    /**
     * Si esta propuesta puede fijar el estado **actual** del barrio, o solo es historia.
     *
     * Un boletín que no dice cuándo ocurre el corte no permite saber si sigue vigente. Al recuperar
     * el histórico de Acuacar eso se volvió crítico: boletines de meses atrás sin ventana declarada
     * caían en `SIN_SERVICIO` y dejaron 128 barrios pintados como sin agua hoy por cortes que ya
     * habían terminado. Un corte inventado destruye la credibilidad (`ADR-006`), así que ante la
     * duda no se toca el mapa: el hecho igual queda en la bitácora.
     *
     * `CON_SERVICIO` es la excepción porque falla hacia el lado seguro: afirmar que hay agua donde
     * el operador dice que la restableció no inventa una emergencia.
     */
    public boolean puedeFijarEstadoActual() {
        return inicioDeclarado != null || estadoPropuesto == EstadoServicio.CON_SERVICIO;
    }

    /**
     * El corte al que pertenece esta propuesta. Un boletín nombra muchos barrios y genera una propuesta
     * por cada uno; el id se deriva del boletín y su ventana para que todas caigan en el mismo corte, en
     * vez de inflar la estadística con un corte por barrio. Es determinista: permite casar la propuesta
     * con su corte (y con el cierre de su barrio) sin guardar una referencia aparte.
     */
    public CorteId idDelCorte() {
        if (inicioDeclarado == null || finPrometido == null) {
            throw new IllegalStateException("Una propuesta sin ventana declarada no tiene corte");
        }
        String semilla = urlOriginal + "|" + inicioDeclarado + "|" + finPrometido;
        return new CorteId(java.util.UUID.nameUUIDFromBytes(
                semilla.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    }

    /**
     * Idempotente sobre una ya aprobada. A diferencia de {@link ReporteCiudadano#aprobar()}, una propuesta
     * descartada no se puede aprobar: aprobar mueve el mapa y anexa a la bitácora (RF028), y una decisión del
     * veedor que ya se cerró no debe revertirse por una segunda petición.
     */
    public PropuestaIngesta aprobar() {
        if (estadoRevision == EstadoRevision.DESCARTADA) {
            throw new IllegalStateException("La propuesta ya fue descartada y no se puede aprobar");
        }
        if (estadoRevision == EstadoRevision.ANULADA) {
            throw new IllegalStateException("La propuesta fue anulada y no se puede aprobar");
        }
        return conRevision(EstadoRevision.APROBADA);
    }

    /** Idempotente sobre una ya descartada; una aprobada no se puede descartar porque ya afectó al mapa. */
    public PropuestaIngesta descartar() {
        if (estadoRevision == EstadoRevision.APROBADA) {
            throw new IllegalStateException("La propuesta ya fue aprobada y no se puede descartar");
        }
        if (estadoRevision == EstadoRevision.ANULADA) {
            throw new IllegalStateException("La propuesta fue anulada y no se puede descartar");
        }
        return conRevision(EstadoRevision.DESCARTADA);
    }

    /**
     * Anular lo que ya movió el mapa: la propuesta deja de afirmar nada del presente y queda con su motivo.
     * Solo se anula una aprobada, porque una pendiente o descartada nunca lo movió.
     */
    public PropuestaIngesta anular(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Anular una propuesta exige un motivo");
        }
        if (estadoRevision != EstadoRevision.APROBADA) {
            throw new IllegalStateException("Solo se anula una propuesta aprobada, y esta está " + estadoRevision);
        }
        return new PropuestaIngesta(id, sectorId, estadoPropuesto, fuente, urlOriginal, citaTextual,
                confianza, detectadaEn, EstadoRevision.ANULADA, inicioDeclarado, finPrometido, imagenUrl, publicadoEn,
                tituloOriginal, motivo, motivoDeRevision);
    }

    /** La misma propuesta, anotando por qué espera al veedor. */
    public PropuestaIngesta conMotivoDeRevision(String motivo) {
        return new PropuestaIngesta(id, sectorId, estadoPropuesto, fuente, urlOriginal, citaTextual,
                confianza, detectadaEn, estadoRevision, inicioDeclarado, finPrometido, imagenUrl, publicadoEn,
                tituloOriginal, motivoAnulacion, motivo);
    }

    /**
     * Un corte anunciado cuya ventana ya terminó hace más de {@code horizonte} (el plazo de expiración): es historia.
     * Al ingerir el histórico de Acuacar eso es lo normal, y publicarlo como un corte vivo dejaría decenas de cortes
     * «abiertos» que solo el barrido de expiración cerraría, sellados con la hora de la recuperación y no con la del hecho.
     * Un restablecimiento no es un corte y no cuenta.
     */
    public boolean esCorteVencido(Instant ahora, java.time.Duration horizonte) {
        return estadoPropuesto != EstadoServicio.CON_SERVICIO && finPrometido != null
                && !ahora.isBefore(finPrometido.plus(horizonte));
    }

    private PropuestaIngesta conRevision(EstadoRevision nueva) {
        return new PropuestaIngesta(id, sectorId, estadoPropuesto, fuente, urlOriginal, citaTextual,
                confianza, detectadaEn, nueva, inicioDeclarado, finPrometido, imagenUrl, publicadoEn, tituloOriginal, null,
                motivoDeRevision);
    }
}
