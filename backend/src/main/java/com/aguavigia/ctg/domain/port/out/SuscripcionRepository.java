package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.Suscripcion;
import com.aguavigia.ctg.domain.SuscripcionId;

import com.aguavigia.ctg.domain.SectorId;
import java.util.List;
import java.util.Optional;

public interface SuscripcionRepository {

    Suscripcion guardar(Suscripcion suscripcion);

    Optional<Suscripcion> buscarPorToken(String token);

    Optional<Suscripcion> buscarPorId(SuscripcionId id);

    List<Suscripcion> buscarConfirmadasPorSector(SectorId sectorId);
}
