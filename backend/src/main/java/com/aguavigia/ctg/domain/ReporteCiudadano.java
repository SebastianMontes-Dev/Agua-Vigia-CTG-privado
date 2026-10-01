package com.aguavigia.ctg.domain;

import java.time.Instant;

public record ReporteCiudadano(
        ReporteId id,
        SectorId sectorId,
        TipoReporte tipo,
        Coordenada coordenada,
        HuellaDispositivo huella,
        Instant timestamp,
        EstadoModeracion estadoModeracion,
        String fotoUrl,
        java.util.Set<String> huellasConfirmacion,
        /**
         * Lo decide quien registra el reporte según el endpoint por el que entró (`/api/iot/presion`, con `X-IoT-Key`),
         * nunca el contenido de la huella: un cliente anónimo no puede hacerse pasar por un sensor.
         */
        boolean esSensor,
        /**
         * Cuánto respalda el reporte que quien lo envía está en el barrio (D16). Los reportes anteriores a D16, que
         * no lo guardaban, se leen como NINGUNA.
         */
        NivelDeVerificacion verificacion,
        /**
         * Resumen de la red desde la que se envió (HMAC de la IP con una clave que rota cada día): sirve para saber si
         * el quórum viene de redes distintas sin guardar la IP. Nulo si no se conoce (sensores, reportes antiguos).
         */
        String redHash) {

    public ReporteCiudadano {
        if (sectorId == null) {
            throw new IllegalArgumentException("El reporte debe estar asociado a un sector");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("El reporte debe tener un tipo");
        }
        if (huella == null) {
            throw new IllegalArgumentException("El reporte debe traer huella de dispositivo (ADR-007)");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("El reporte debe tener timestamp");
        }
        if (estadoModeracion == null) {
            throw new IllegalArgumentException("El reporte debe tener un estado de moderación");
        }
        if (huellasConfirmacion == null) {
            huellasConfirmacion = java.util.Collections.emptySet();
        } else {
            huellasConfirmacion = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(huellasConfirmacion));
        }
        if (verificacion == null) {
            verificacion = NivelDeVerificacion.NINGUNA;
        }
    }

    /** Un reporte con sensor o sin él, sin verificación ni red: el que se construía antes de D16. */
    public ReporteCiudadano(ReporteId id, SectorId sectorId, TipoReporte tipo, Coordenada coordenada,
                             HuellaDispositivo huella, Instant timestamp, EstadoModeracion estadoModeracion,
                             String fotoUrl, java.util.Set<String> huellasConfirmacion, boolean esSensor) {
        this(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl, huellasConfirmacion,
                esSensor, NivelDeVerificacion.NINGUNA, null);
    }

    /** Un reporte de ciudadano: no de un sensor. */
    public ReporteCiudadano(ReporteId id, SectorId sectorId, TipoReporte tipo, Coordenada coordenada,
                             HuellaDispositivo huella, Instant timestamp, EstadoModeracion estadoModeracion,
                             String fotoUrl, java.util.Set<String> huellasConfirmacion) {
        this(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl, huellasConfirmacion, false);
    }

    /** RF005-RF008: un reporte recién creado siempre nace PENDIENTE de moderación (RF018, `ADR-023`). */
    public ReporteCiudadano(ReporteId id, SectorId sectorId, TipoReporte tipo, Coordenada coordenada,
                             HuellaDispositivo huella, Instant timestamp) {
        this(id, sectorId, tipo, coordenada, huella, timestamp, EstadoModeracion.PENDIENTE, null, java.util.Collections.emptySet());
    }

    public ReporteCiudadano(ReporteId id, SectorId sectorId, TipoReporte tipo, Coordenada coordenada,
                             HuellaDispositivo huella, Instant timestamp, EstadoModeracion estadoModeracion) {
        this(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, null, java.util.Collections.emptySet());
    }

    public ReporteCiudadano(ReporteId id, SectorId sectorId, TipoReporte tipo, Coordenada coordenada,
                             HuellaDispositivo huella, Instant timestamp, EstadoModeracion estadoModeracion, String fotoUrl) {
        this(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl, java.util.Collections.emptySet());
    }

    /** RF018 — idempotente: aprobar un reporte ya aprobado, o cambiar de un descarte a aprobado, no falla. */
    public ReporteCiudadano aprobar() {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, EstadoModeracion.APROBADO, fotoUrl, huellasConfirmacion, esSensor, verificacion, redHash);
    }

    /** RF018 — idempotente, igual que {@link #aprobar()}. */
    public ReporteCiudadano descartar() {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, EstadoModeracion.DESCARTADO, fotoUrl, huellasConfirmacion, esSensor, verificacion, redHash);
    }

    public ReporteCiudadano conFoto(String fotoUrl) {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl, huellasConfirmacion, esSensor, verificacion, redHash);
    }

    public ReporteCiudadano confirmar(HuellaDispositivo huellaConfirmacion) {
        if (this.huella.equals(huellaConfirmacion) || this.huellasConfirmacion.contains(huellaConfirmacion.hash())) {
            return this;
        }
        java.util.Set<String> nuevas = new java.util.HashSet<>(this.huellasConfirmacion);
        nuevas.add(huellaConfirmacion.hash());
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl, nuevas, esSensor, verificacion, redHash);
    }

    /** El mismo reporte, marcado como de un sensor de la red. */
    public ReporteCiudadano comoDeSensor() {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl,
                huellasConfirmacion, true, verificacion, redHash);
    }

    /** El mismo reporte con lo que respalda de dónde viene: el nivel de verificación y la red (D16). */
    public ReporteCiudadano conIdentidad(NivelDeVerificacion nivel, String redHash) {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl,
                huellasConfirmacion, esSensor, nivel, redHash);
    }

    public int numeroConfirmaciones() {
        return huellasConfirmacion.size();
    }
}
