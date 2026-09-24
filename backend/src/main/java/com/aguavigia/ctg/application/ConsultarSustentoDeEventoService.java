package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.EventoId;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.port.in.ConsultarSustentoDeEventoUseCase;
import com.aguavigia.ctg.domain.port.out.EventoBitacoraRepository;

public class ConsultarSustentoDeEventoService implements ConsultarSustentoDeEventoUseCase {

    private final EventoBitacoraRepository eventos;

    public ConsultarSustentoDeEventoService(EventoBitacoraRepository eventos) {
        this.eventos = eventos;
    }

    @Override
    public Pagina<ReporteId> sustento(EventoId eventoId, Integer pagina, Integer tamano) {
        EventoBitacora evento = eventos.buscarPorId(eventoId).orElseThrow(() ->
                new EntidadNoEncontradaException("No existe el evento '" + eventoId.valor() + "'"));
        return Pagina.deLista(evento.reportesSustento(), Pagina.paginaValida(pagina), Pagina.tamanoValido(tamano));
    }
}
