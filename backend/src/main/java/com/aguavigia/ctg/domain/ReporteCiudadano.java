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
        String redHash,
        /** SHA-256 de la foto tal como se guardó (ya recomprimida): permite reconocer la misma imagen en otro reporte. */
        String fotoSha256,
        /** El veedor descartó la foto (no el reporte): se conserva la URL, pero no se sirve al público. */
        boolean fotoDescartada) {

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
                esSensor, NivelDeVerificacion.NINGUNA, null, null, false);
    }

    /** El reporte con identidad pero todavía sin foto, que es como lo registra RegistrarReporteService. */
    public ReporteCiudadano(ReporteId id, SectorId sectorId, TipoReporte tipo, Coordenada coordenada,
                             HuellaDispositivo huella, Instant timestamp, EstadoModeracion estadoModeracion,
                             String fotoUrl, java.util.Set<String> huellasConfirmacion, boolean esSensor,
                             NivelDeVerificacion verificacion, String redHash) {
        this(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl, huellasConfirmacion,
                esSensor, verificacion, redHash, null, false);
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
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, EstadoModeracion.APROBADO, fotoUrl, huellasConfirmacion, esSensor, verificacion, redHash, fotoSha256, fotoDescartada);
    }

    /** RF018 — idempotente, igual que {@link #aprobar()}. */
    public ReporteCiudadano descartar() {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, EstadoModeracion.DESCARTADO, fotoUrl, huellasConfirmacion, esSensor, verificacion, redHash, fotoSha256, fotoDescartada);
    }

    public ReporteCiudadano conFoto(String fotoUrl) {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl, huellasConfirmacion, esSensor, verificacion, redHash, fotoSha256, fotoDescartada);
    }

    public ReporteCiudadano confirmar(HuellaDispositivo huellaConfirmacion) {
        if (this.huella.equals(huellaConfirmacion) || this.huellasConfirmacion.contains(huellaConfirmacion.hash())) {
            return this;
        }
        java.util.Set<String> nuevas = new java.util.HashSet<>(this.huellasConfirmacion);
        nuevas.add(huellaConfirmacion.hash());
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl, nuevas, esSensor, verificacion, redHash, fotoSha256, fotoDescartada);
    }

    /** El mismo reporte, marcado como de un sensor de la red. */
    public ReporteCiudadano comoDeSensor() {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl,
                huellasConfirmacion, true, verificacion, redHash, fotoSha256, fotoDescartada);
    }

    /** El mismo reporte con lo que respalda de dónde viene: el nivel de verificación y la red (D16). */
    public ReporteCiudadano conIdentidad(NivelDeVerificacion nivel, String redHash) {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl,
                huellasConfirmacion, esSensor, nivel, redHash, fotoSha256, fotoDescartada);
    }

    /** El mismo reporte con su foto y el SHA-256 de lo que se guardó. Una foto nueva reemplaza un descarte anterior. */
    public ReporteCiudadano conFoto(String fotoUrl, String fotoSha256) {
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl,
                huellasConfirmacion, esSensor, verificacion, redHash, fotoSha256, false);
    }

    /** El veedor descarta la foto sin descartar el reporte: se conserva la URL, pero deja de servirse al público. */
    public ReporteCiudadano descartarFoto() {
        if (fotoUrl == null || fotoDescartada) {
            return this;
        }
        return new ReporteCiudadano(id, sectorId, tipo, coordenada, huella, timestamp, estadoModeracion, fotoUrl,
                huellasConfirmacion, esSensor, verificacion, redHash, fotoSha256, true);
    }

    /** La foto se sirve al público solo si el reporte está aprobado y la foto no se descartó. */
    public boolean fotoEsPublica() {
        return fotoUrl != null && !fotoDescartada && estadoModeracion == EstadoModeracion.APROBADO;
    }

    public EstadoDeFoto estadoDeFoto() {
        if (fotoUrl == null) {
            return EstadoDeFoto.SIN_FOTO;
        }
        if (fotoDescartada || estadoModeracion == EstadoModeracion.DESCARTADO) {
            return EstadoDeFoto.DESCARTADA;
        }
        return estadoModeracion == EstadoModeracion.APROBADO ? EstadoDeFoto.PUBLICA : EstadoDeFoto.EN_REVISION;
    }

    /** El nombre del archivo, sea la URL la vieja (/fotos/x.jpg) o la nueva (/api/fotos/x.jpg). */
    public java.util.Optional<String> nombreDeFoto() {
        if (fotoUrl == null || fotoUrl.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(fotoUrl.substring(fotoUrl.lastIndexOf('/') + 1));
    }

    public int numeroConfirmaciones() {
        return huellasConfirmacion.size();
    }
}
