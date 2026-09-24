package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.ChatTelegramId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionTelegram;
import com.aguavigia.ctg.domain.port.out.SuscripcionTelegramRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SuscripcionTelegramMongoAdapter implements SuscripcionTelegramRepository {

    private final SuscripcionTelegramMongoRepository repositorio;

    public SuscripcionTelegramMongoAdapter(SuscripcionTelegramMongoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Optional<SuscripcionTelegram> buscarPorChat(ChatTelegramId chat) {
        return repositorio.findById(String.valueOf(chat.valor())).map(SuscripcionTelegramMongoAdapter::aDominio);
    }

    @Override
    public SuscripcionTelegram guardar(SuscripcionTelegram suscripcion) {
        SuscripcionTelegramDocumento documento = new SuscripcionTelegramDocumento();
        documento.setId(String.valueOf(suscripcion.chat().valor()));
        documento.setSectorIds(suscripcion.sectorIds().stream().map(SectorId::valor).toList());
        documento.setCreadaEn(suscripcion.creadaEn());
        repositorio.save(documento);
        return suscripcion;
    }

    @Override
    public void eliminar(ChatTelegramId chat) {
        repositorio.deleteById(String.valueOf(chat.valor()));
    }

    @Override
    public List<SuscripcionTelegram> buscarPorSector(SectorId sectorId) {
        return repositorio.findBySectorIdsContaining(sectorId.valor()).stream()
                .map(SuscripcionTelegramMongoAdapter::aDominio)
                .toList();
    }

    private static SuscripcionTelegram aDominio(SuscripcionTelegramDocumento documento) {
        return new SuscripcionTelegram(
                new ChatTelegramId(Long.parseLong(documento.getId())),
                documento.getSectorIds().stream().map(SectorId::new).toList(),
                documento.getCreadaEn());
    }
}
