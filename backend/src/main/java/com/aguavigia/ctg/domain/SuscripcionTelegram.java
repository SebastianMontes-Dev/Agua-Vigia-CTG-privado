package com.aguavigia.ctg.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * RF041 — un chat de Telegram que sigue uno o varios sectores. No hay doble opt-in como en el correo
 * (RF013): solo la persona puede escribirle al bot desde su chat, así que el propio mensaje es la
 * confirmación. La baja elimina el registro entero, porque el id del chat es un dato personal (RNF009).
 */
public record SuscripcionTelegram(ChatTelegramId chat, List<SectorId> sectorIds, Instant creadaEn) {

    /** Tope por chat: impide que un solo chat cargue cientos de sectores. */
    public static final int MAXIMO_SECTORES = 10;

    public SuscripcionTelegram {
        if (chat == null) {
            throw new IllegalArgumentException("La suscripción debe tener un chat");
        }
        if (sectorIds == null) {
            throw new IllegalArgumentException("La suscripción debe tener una lista de sectores");
        }
        if (creadaEn == null) {
            throw new IllegalArgumentException("La suscripción debe tener fecha de creación");
        }
        sectorIds = List.copyOf(new LinkedHashSet<>(sectorIds));
        if (sectorIds.size() > MAXIMO_SECTORES) {
            throw new IllegalArgumentException("Un chat no puede seguir más de " + MAXIMO_SECTORES + " sectores");
        }
    }

    public static SuscripcionTelegram nueva(ChatTelegramId chat, Instant ahora) {
        return new SuscripcionTelegram(chat, List.of(), ahora);
    }

    public boolean sigue(SectorId sectorId) {
        return sectorIds.contains(sectorId);
    }

    /** Idempotente: seguir dos veces el mismo sector no lo duplica. */
    public SuscripcionTelegram siguiendo(SectorId sectorId) {
        if (sigue(sectorId)) {
            return this;
        }
        if (sectorIds.size() >= MAXIMO_SECTORES) {
            throw new IllegalStateException("Ya sigues " + MAXIMO_SECTORES + " sectores, que es el máximo");
        }
        List<SectorId> nuevos = new ArrayList<>(sectorIds);
        nuevos.add(sectorId);
        return new SuscripcionTelegram(chat, nuevos, creadaEn);
    }

    /** Idempotente: dejar de seguir un sector que no se seguía no falla. */
    public SuscripcionTelegram sinSeguir(SectorId sectorId) {
        List<SectorId> restantes = new ArrayList<>(sectorIds);
        restantes.remove(sectorId);
        return new SuscripcionTelegram(chat, restantes, creadaEn);
    }

    public boolean sinSectores() {
        return sectorIds.isEmpty();
    }
}
