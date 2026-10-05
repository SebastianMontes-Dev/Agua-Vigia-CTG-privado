package com.aguavigia.ctg.domain;

import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RF026 — única vía de creación de negocio para `EventoBitacora`, con un método por caso de
 * evento en vez de un constructor genérico. `EventoBitacoraMongoAdapter.aDominio()` es la
 * excepción documentada: rehidrata un evento que ya existió, no crea uno nuevo, así que sigue
 * usando el constructor del record directamente (ver Javadoc de `EventoBitacora`).
 */
public final class EventoBitacoraFactory {

    private EventoBitacoraFactory() {
    }

    public static EventoBitacora corteAnunciado(CorteAgua corte, SectorId sectorId, Instant ahora) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.CORTE_ANUNCIADO,
                sectorId,
                corte.id(),
                ahora,
                "Corte oficial anunciado en '%s': %s".formatted(sectorId.valor(), corte.causa()))
                .conFuente(corte.origen() == OrigenCorte.VEEDOR ? OrigenEstado.VEEDOR : OrigenEstado.ACUACAR, null);
    }

    public static EventoBitacora corteRestablecido(CorteAgua corte, SectorId sectorId, Instant ahora) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.CORTE_RESTABLECIDO,
                sectorId,
                corte.id(),
                ahora,
                "Corte restablecido en '%s'".formatted(sectorId.valor()))
                .conFuente(OrigenEstado.VEEDOR, null);
    }

    /** El corte se anuló: la bitácora no se edita, así que la corrección queda como un evento con su motivo. */
    public static EventoBitacora corteAnulado(CorteAgua corte, SectorId sectorId, Instant ahora) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.CORTE_ANULADO,
                sectorId,
                corte.id(),
                ahora,
                "Corte anulado en '%s': %s".formatted(sectorId.valor(), corte.motivoAnulacion()));
    }

    /** Un boletín aprobado resultó ser un error: se anexa la corrección y se cita el boletín que se anuló. */
    public static EventoBitacora boletinAnulado(SectorId sectorId, String motivo, String urlOriginal, Instant ahora) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.CORTE_ANULADO,
                sectorId,
                null,
                ahora,
                "Aviso anulado en '%s': %s".formatted(sectorId.valor(), motivo),
                null,
                urlOriginal,
                null);
    }

    /** Nadie confirmó el restablecimiento a tiempo: sin estado que afirmar, el barrio vuelve a «sin datos». */
    public static EventoBitacora corteExpirado(CorteAgua corte, SectorId sectorId, Instant ahora) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.CORTE_EXPIRADO,
                sectorId,
                corte.id(),
                ahora,
                "El corte en '%s' venció sin que nadie confirmara el restablecimiento; el barrio vuelve a «sin datos»"
                        .formatted(sectorId.valor()));
    }

    /**
     * Un corte que Acuacar anunció y que ya había terminado, sin confirmación alguna, cuando se ingirió (D27). Lleva la
     * fecha del hecho —el inicio que el boletín declara—, no la de la recuperación: la bitácora cuenta qué le pasó al
     * acueducto y no cuándo corrió el colector. No afirma ningún estado: una ventana vencida no prueba que haya agua.
     */
    public static EventoBitacora corteHistorico(PropuestaIngesta propuesta) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.CORTE_EXPIRADO,
                propuesta.sectorId(),
                propuesta.idDelCorte(),
                propuesta.inicioDeclarado(),
                "Acuacar anunció un corte en '%s' del %s al %s; al registrarlo ya había terminado y no consta cuándo volvió el servicio"
                        .formatted(propuesta.sectorId().valor(), propuesta.inicioDeclarado(), propuesta.finPrometido()),
                null,
                propuesta.urlOriginal(),
                propuesta.imagenUrl())
                .conFuente(OrigenEstado.ACUACAR, null);
    }

    /** Los vecinos confirmaron que volvió el agua; quedan citados los reportes que lo sostienen. */
    public static EventoBitacora restablecimientoPorVecinos(SectorId sectorId, List<ReporteId> reportesQueSustentan,
                                                              RespaldoVecinal respaldo, Instant ahora) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.RESTABLECIMIENTO_POR_VECINOS,
                sectorId,
                null,
                ahora,
                "%d reportes ciudadanos independientes confirmaron que volvió el servicio en '%s'"
                        .formatted(reportesQueSustentan.size(), sectorId.valor()),
                EstadoServicio.CON_SERVICIO,
                null,
                null,
                reportesQueSustentan)
                .conFuente(OrigenEstado.VECINOS, respaldo);
    }

    /** Un quórum de vecinos contradice a la fuente oficial, sin cambiar el color del barrio. */
    public static EventoBitacora estadoEnDisputa(SectorId sectorId, EstadoServicio estadoQueSeDiscute, int vecinos,
                                                   Instant ahora) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.ESTADO_EN_DISPUTA,
                sectorId,
                null,
                ahora,
                "%d vecinos contradicen el estado oficial de '%s'".formatted(vecinos, sectorId.valor()),
                estadoQueSeDiscute,
                null,
                null)
                .conFuente(OrigenEstado.VECINOS, null);
    }

    /** Un estado que movió el mapa se cayó al descartar los reportes que lo sostenían. */
    public static EventoBitacora consensoRevertido(SectorId sectorId, Instant ahora) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.CONSENSO_REVERTIDO,
                sectorId,
                null,
                ahora,
                "Al descartar reportes, el consenso de vecinos en '%s' dejó de sostenerse".formatted(sectorId.valor()))
                .conFuente(OrigenEstado.VECINOS, null);
    }

    /**
     * RF011 — `reportesQueSustentan` viaja en el evento: la bitácora es de solo anexado (RF028) y un
     * conteo suelto no permite contrastar el cambio con la evidencia que lo sostuvo.
     */
    public static EventoBitacora consensoConfirmado(SectorId sectorId, EstadoServicio nuevoEstado,
                                                      List<ReporteId> reportesQueSustentan,
                                                      RespaldoVecinal respaldo, Instant ahora) {
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS,
                sectorId,
                null,
                ahora,
                "%d reportes ciudadanos independientes confirmaron %s en '%s'"
                        .formatted(reportesQueSustentan.size(), nuevoEstado, sectorId.valor()),
                nuevoEstado,
                null,
                null,
                reportesQueSustentan)
                .conFuente(OrigenEstado.VECINOS, respaldo);
    }

    // --- lo que deja un recálculo del estado de un barrio ---------------------------------------------------------

    /**
     * Lo que se anexa a la bitácora cuando se recalcula un barrio, en orden: que el consenso se revirtió si el estado cambió porque se
     * descartaron los reportes que lo sostenían; después, o la reapertura de un corte (que provocaron los vecinos aunque el estado lo
     * afirme de nuevo la fuente oficial) o el cambio de estado o, si solo se abrió una disputa, esa disputa (se anota al abrirse, no
     * en cada minuto que sigue abierta).
     *
     * @param quorumQueReabrio el quórum que contradijo un cierre provisional y reabrió el corte; nulo si no hubo reapertura
     */
    public static List<EventoBitacora> delRecalculo(Sector sector, EstadoPublicado publicado, List<PropuestaIngesta> aprobadas,
                                                    QuorumVecinos quorumQueReabrio, VotosDeVecinos vecinos,
                                                    boolean cambiaElEstado, boolean memoriaDescartada, Instant ahora) {
        List<EventoBitacora> eventos = new ArrayList<>();
        if (cambiaElEstado && memoriaDescartada) {
            eventos.add(consensoRevertido(sector.id(), ahora));
        }
        if (quorumQueReabrio != null) {
            // Reabrir el corte es un hecho de la bitácora aunque el estado ya fuera el que correspondía.
            delaReapertura(sector, quorumQueReabrio, vecinos, ahora).ifPresent(eventos::add);
        } else if (cambiaElEstado) {
            delCambioDeEstado(sector, publicado, aprobadas, vecinos, ahora).ifPresent(eventos::add);
        } else if (publicado.enDisputa() && !sector.marcas().enDisputa()) {
            eventos.add(estadoEnDisputa(sector.id(), publicado.estado(), publicado.reportesEnContra(), ahora));
        }
        return eventos;
    }

    /**
     * Quién ve el cambio depende de quién lo sostiene: los vecinos dejan el consenso con sus reportes; un boletín, su cita textual. El
     * corte del veedor no anexa nada aquí porque ya dejó su evento al registrarse o cerrarse, y volver a «sin datos» no es una noticia.
     */
    public static Optional<EventoBitacora> delCambioDeEstado(Sector sector, EstadoPublicado publicado,
                                                             List<PropuestaIngesta> aprobadas, VotosDeVecinos vecinos,
                                                             Instant ahora) {
        EstadoServicio estado = publicado.estado();
        if (estado == null || publicado.origen() == null) {
            return Optional.empty();
        }
        return switch (publicado.origen()) {
            case VECINOS, SENSOR -> {
                List<ReporteId> ids = idsDe(vecinos.sustentoDe(estado));
                if (ids.isEmpty()) {
                    yield Optional.empty();
                }
                EventoBitacora evento = estado == EstadoServicio.CON_SERVICIO
                        ? restablecimientoPorVecinos(sector.id(), ids, publicado.respaldo(), ahora)
                        : consensoConfirmado(sector.id(), estado, ids, publicado.respaldo(), ahora);
                yield Optional.of(evento.conFuente(publicado.origen(), publicado.respaldo()));
            }
            case ACUACAR, PRENSA -> propuestaQueSustenta(publicado, aprobadas)
                    .map(p -> detectadoPorIngesta(sector.id(), sector.nombre(), estado,
                            p.fuente(), p.urlOriginal(), p.imagenUrl(), p.tituloOriginal(), ahora));
            case VEEDOR -> Optional.empty();
        };
    }

    /** Un corte que se reabre lo provocaron los vecinos (o los sensores): se cita su quórum, no el boletín de antes. */
    public static Optional<EventoBitacora> delaReapertura(Sector sector, QuorumVecinos contradice, VotosDeVecinos vecinos,
                                                          Instant ahora) {
        List<ReporteCiudadano> sustento = vecinos.sustentoPorTipo().getOrDefault(contradice.tipo(), List.of());
        if (sustento.isEmpty()) {
            return Optional.empty();
        }
        RespaldoVecinal respaldo = new RespaldoVecinal(contradice.respaldo(), contradice.umbral());
        return Optional.of(consensoConfirmado(sector.id(), contradice.estado(), idsDe(sustento), respaldo, ahora)
                .conFuente(OrigenEstado.deLosVotos(sustento), respaldo));
    }

    /** El boletín de la fuente que sostiene el estado y cuya ventana es la que se publicó; el más reciente gana. */
    private static Optional<PropuestaIngesta> propuestaQueSustenta(EstadoPublicado publicado, List<PropuestaIngesta> aprobadas) {
        boolean oficial = publicado.origen() == OrigenEstado.ACUACAR;
        return aprobadas.stream()
                .filter(p -> p.esDeFuenteOficial() == oficial)
                .filter(p -> publicado.estado() == EstadoServicio.CON_SERVICIO
                        ? p.estadoPropuesto() == EstadoServicio.CON_SERVICIO
                        : p.estadoPropuesto() != EstadoServicio.CON_SERVICIO && p.tieneLaVentanaDe(publicado.ventanaPrometida()))
                .max(Comparator.<PropuestaIngesta, Instant>comparing(PropuestaIngesta::momento)
                        .thenComparing(p -> p.id().valor()));
    }

    private static List<ReporteId> idsDe(List<ReporteCiudadano> reportes) {
        return reportes.stream().map(ReporteCiudadano::id).toList();
    }

    /**
     * M9 — RF026: la ingesta automatizada cambia estado igual que el consenso ciudadano, así que
     * también anexa.
     *
     * La descripción se redacta para un vecino, no para un log: antes decía *"Ingesta automatizada
     * (acuacar) detectó SIN_SERVICIO en 'pasacaballos'"*, con el identificador interno del barrio y
     * el nombre del enum. La bitácora es la cara pública de la plataforma (RF026); si hay que
     * traducirla mentalmente, no informa.
     */
    public static EventoBitacora detectadoPorIngesta(SectorId sectorId, String nombreDelSector,
                                                       EstadoServicio nuevoEstado, String fuente,
                                                       String urlOriginal, String imagenUrl,
                                                       String tituloOriginal, Instant ahora) {
        String barrio = enCapitalizacionDeNombre(
                nombreDelSector == null || nombreDelSector.isBlank() ? sectorId.valor() : nombreDelSector);
        return new EventoBitacora(
                new EventoId(UUID.randomUUID().toString()),
                TipoEvento.CORTE_DETECTADO_POR_INGESTA,
                sectorId,
                null,
                ahora,
                descripcion(tituloOriginal, nuevoEstado, barrio, fuente),
                nuevoEstado,
                urlOriginal,
                imagenUrl)
                .conFuente("acuacar".equalsIgnoreCase(fuente) ? OrigenEstado.ACUACAR : OrigenEstado.PRENSA, null);
    }

    /**
     * Se enseña el titular tal como lo publicó la fuente, no una frase nuestra: la bitácora es un
     * registro de lo que dijo el operador, y `ADR-006` pide que todo lo publicado se pueda
     * contrastar con el original. La frase compuesta queda solo de respaldo, para las fuentes que
     * no traen titular.
     */
    private static String descripcion(String tituloOriginal, EstadoServicio estado, String barrio,
                                       String fuente) {
        String titular = limpiarTitular(tituloOriginal);
        if (!titular.isBlank()) {
            return titular;
        }
        return "%s en %s, según %s".formatted(enPalabras(estado), barrio, nombreDeLaFuente(fuente));
    }

    /**
     * Acuacar antepone el número del boletín al titular (`#2854-AGUAS DE CARTAGENA…`). Se quita
     * porque la tarjeta ya lo muestra como insignia sobre la portada, y repetirlo dentro del texto
     * roba espacio a lo que sí informa. El resto del titular no se toca.
     */
    private static String limpiarTitular(String titulo) {
        if (titulo == null) {
            return "";
        }
        return titulo.replaceFirst("^\\s*#?\\s*\\d{3,5}\\s*[-–—]?\\s*", "").trim();
    }

    /**
     * El catálogo de barrios viene del GeoJSON oficial, que los escribe en mayúscula sostenida
     * (`VISTA HERMOSA`). En una frase corrida eso se lee como un grito, así que se capitaliza para
     * la bitácora. Las palabras de enlace se dejan en minúscula —`Ciudadela de la Paz`, no
     * `Ciudadela De La Paz`— que es como se escriben los nombres propios en español.
     */
    private static String enCapitalizacionDeNombre(String nombre) {
        String[] palabras = nombre.trim().toLowerCase().split("\\s+");
        StringBuilder resultado = new StringBuilder(nombre.length());
        for (int i = 0; i < palabras.length; i++) {
            String palabra = palabras[i];
            if (palabra.isEmpty()) {
                continue;
            }
            if (i > 0) {
                resultado.append(' ');
            }
            if (i > 0 && ENLACES.contains(palabra)) {
                resultado.append(palabra);
            } else {
                resultado.append(Character.toUpperCase(palabra.charAt(0))).append(palabra.substring(1));
            }
        }
        return resultado.toString();
    }

    private static final java.util.Set<String> ENLACES =
            java.util.Set.of("de", "del", "la", "las", "el", "los", "y", "en");

    private static String enPalabras(EstadoServicio estado) {
        return switch (estado) {
            case CON_SERVICIO -> "Servicio restablecido";
            case SIN_SERVICIO -> "Suspensión del servicio";
            case CORTE_PROGRAMADO -> "Corte programado";
            case PRESION_BAJA -> "Baja presión";
        };
    }

    /** `acuacar` es el operador y se nombra como tal; los feeds de prensa ya vienen con su medio. */
    private static String nombreDeLaFuente(String fuente) {
        if (fuente == null || fuente.isBlank()) {
            return "la fuente oficial";
        }
        if ("acuacar".equalsIgnoreCase(fuente)) {
            return "Acuacar";
        }
        return fuente.replace('-', ' ');
    }
}
