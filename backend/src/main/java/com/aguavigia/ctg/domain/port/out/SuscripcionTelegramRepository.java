package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.ChatTelegramId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionTelegram;

import java.util.List;
import java.util.Optional;

public interface SuscripcionTelegramRepository {

    Optional<SuscripcionTelegram> buscarPorChat(ChatTelegramId chat);

    SuscripcionTelegram guardar(SuscripcionTelegram suscripcion);

    /** RNF009 — la baja borra el registro: el id del chat es un dato personal. */
    void eliminar(ChatTelegramId chat);

    List<SuscripcionTelegram> buscarPorSector(SectorId sectorId);
}
