package com.aguavigia.ctg.infrastructure.eventos;

import com.aguavigia.ctg.domain.DescripcionDeEstado;
import com.aguavigia.ctg.domain.port.in.EnviarAlertaPushUseCase;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AlertaPushSectorListener {

    private final EnviarAlertaPushUseCase enviarAlertaPushUseCase;

    public AlertaPushSectorListener(EnviarAlertaPushUseCase enviarAlertaPushUseCase) {
        this.enviarAlertaPushUseCase = enviarAlertaPushUseCase;
    }

    @Async
    @EventListener
    public void onSectorActualizado(SectorActualizadoEvent event) {
        String mensaje = String.format("AguaVigía: el sector %s ahora está %s.",
                event.sector().nombre(),
                DescripcionDeEstado.describir(event.sector().estadoActual()));
        
        enviarAlertaPushUseCase.enviar(event.sector().id(), mensaje);
    }
}
