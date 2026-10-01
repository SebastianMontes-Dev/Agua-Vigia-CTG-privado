package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.Sector;

import java.util.List;

/** Los barrios que un quórum de vecinos contradice sin cambiar su color: es lo que el veedor debe resolver. */
public interface ListarDisputasUseCase {

    /** Los que más vecinos contradicen, primero. */
    List<Sector> listar();
}
