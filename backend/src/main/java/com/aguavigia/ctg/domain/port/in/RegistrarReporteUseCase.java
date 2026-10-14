package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;

/** RF005-RF007 — reportar en máximo dos toques, con un token de dispositivo o desde la cuenta de un vecino. */
public interface RegistrarReporteUseCase {

    /**
     * {@code esSensor} lo decide quien llama (el controlador), nunca el contenido de la identidad: solo
     * {@code IotController} puede pasar {@code true}, y solo después de validar {@code X-IoT-Key}.
     *
     * @param precisionMetros precisión de la coordenada, si viaja; sin ella la ubicación no verifica nada
     * @param ip la IP del cliente, de la que solo se guarda un resumen diario (nulo si no se conoce)
     */
    ReporteCiudadano registrar(SectorId sectorId, TipoReporte tipo, Coordenada coordenada, Double precisionMetros,
                               Reportante reportante, String ip, boolean esSensor);

    /**
     * Un reporte sin cuenta, sin precisión de ubicación y sin IP: el camino de los sensores, que se autentican con
     * su clave y no tienen nada que verificar.
     */
    default ReporteCiudadano registrar(SectorId sectorId, TipoReporte tipo, Coordenada coordenada,
                                       HuellaDispositivo huella, boolean esSensor) {
        return registrar(sectorId, tipo, coordenada, null, Reportante.anonimo(huella), null, esSensor);
    }
}
