package com.aguavigia.ctg.application;

import com.aguavigia.ctg.compartido.reloj.Reloj;
import com.aguavigia.ctg.domain.Dispositivo;
import com.aguavigia.ctg.domain.DispositivoId;
import com.aguavigia.ctg.domain.port.in.EmitirTokenDeDispositivoUseCase;
import com.aguavigia.ctg.domain.port.out.DispositivoRepository;
import com.aguavigia.ctg.domain.port.out.FirmaDeDispositivosPort;

import java.time.Instant;
import java.util.UUID;

/**
 * Crear una identidad cuesta una petición, y esa petición tiene límite por IP (`application.yml`):
 * es lo que hace que fabricar identidades para llenar un quórum sea caro, a diferencia de la
 * `huella` que antes inventaba el cliente sin ningún costo.
 */
public class EmitirTokenDeDispositivoService implements EmitirTokenDeDispositivoUseCase {

    private final DispositivoRepository dispositivos;
    private final FirmaDeDispositivosPort firma;
    private final Reloj reloj;

    public EmitirTokenDeDispositivoService(DispositivoRepository dispositivos, FirmaDeDispositivosPort firma,
                                           Reloj reloj) {
        this.dispositivos = dispositivos;
        this.firma = firma;
        this.reloj = reloj;
    }

    @Override
    public String emitir() {
        Instant ahora = reloj.ahora();
        Dispositivo nuevo = dispositivos.guardar(
                new Dispositivo(new DispositivoId(UUID.randomUUID().toString()), ahora, ahora));
        return firma.emitir(nuevo.id());
    }
}
