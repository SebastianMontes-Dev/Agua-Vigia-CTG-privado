package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;

/** RF005-RF007 — reportar sin registro, en máximo dos toques. */
public interface RegistrarReporteUseCase {

    /**
     * {@code esSensor} lo decide quien llama (el controlador), nunca el contenido de
     * {@code huella}: solo {@code IotController} puede pasar {@code true}, y solo después de
     * validar {@code X-IoT-Key}. Ver javadoc de {@link com.aguavigia.ctg.domain.HuellaDispositivo}.
     */
    ReporteCiudadano registrar(SectorId sectorId, TipoReporte tipo, Coordenada coordenada,
                               HuellaDispositivo huella, boolean esSensor);
}
