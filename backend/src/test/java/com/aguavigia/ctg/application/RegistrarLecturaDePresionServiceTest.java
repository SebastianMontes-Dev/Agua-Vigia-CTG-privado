package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RegistrarLecturaDePresionServiceTest {

    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");

    private SectorRepository sectores;
    private RegistrarReporteUseCase registrarReporte;
    private RegistrarLecturaDePresionService servicio;

    @BeforeEach
    void montar() {
        sectores = mock(SectorRepository.class);
        registrarReporte = mock(RegistrarReporteUseCase.class);
        servicio = new RegistrarLecturaDePresionService(sectores, registrarReporte, 15.0, 20.0);
        given(sectores.buscarPorId(BOCAGRANDE)).willReturn(
                Optional.of(new Sector(BOCAGRANDE, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO)));
    }

    @Test
    void unaPresionPorDebajoDelUmbralDebeRegistrarUnReporteDeSensor() {
        Coordenada coordenada = new Coordenada(10.40, -75.55);

        servicio.registrar("sensor-1", BOCAGRANDE, 12.0, coordenada);

        verify(registrarReporte).registrar(BOCAGRANDE, TipoReporte.PRESION_BAJA, coordenada,
                HuellaDispositivo.deSensor("sensor-1"), true);
    }

    /** Un sensor con presión normal es un voto de que volvió el servicio: antes se ignoraba y nunca lo confirmaba. */
    @Test
    void unaPresionNormalDebeRegistrarUnReporteDeServicioRestablecido() {
        Coordenada coordenada = new Coordenada(10.40, -75.55);

        servicio.registrar("sensor-1", BOCAGRANDE, 40.0, coordenada);

        verify(registrarReporte).registrar(BOCAGRANDE, TipoReporte.SERVICIO_RESTABLECIDO, coordenada,
                HuellaDispositivo.deSensor("sensor-1"), true);
    }

    /** El umbral normal es «mayor o igual»: justo 20 psi ya cuenta como presión normal. */
    @Test
    void unaPresionIgualAlUmbralNormalDebeRegistrarServicioRestablecido() {
        servicio.registrar("sensor-1", BOCAGRANDE, 20.0, null);

        verify(registrarReporte).registrar(eq(BOCAGRANDE), eq(TipoReporte.SERVICIO_RESTABLECIDO), any(), any(), eq(true));
    }

    /**
     * Histéresis: entre 15 y 20 psi el sensor no vota. Sin esta banda un sensor que oscila en torno a 15 psi
     * votaría «presión baja» y «servicio restablecido» alternadamente y el barrio parpadearía.
     */
    @Test
    void unaPresionEnLaBandaIntermediaNoDebeRegistrarNada() {
        servicio.registrar("sensor-1", BOCAGRANDE, 17.5, null);

        verify(registrarReporte, never()).registrar(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void elUmbralNormalNoPuedeSerMenorQueElDePresionBaja() {
        assertThatThrownBy(() -> new RegistrarLecturaDePresionService(sectores, registrarReporte, 20.0, 15.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** El umbral es «menor que»: justo 15 psi todavía es presión normal. */
    @Test
    void unaPresionIgualAlUmbralNoDebeRegistrarNada() {
        servicio.registrar("sensor-1", BOCAGRANDE, 15.0, null);

        verify(registrarReporte, never()).registrar(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void unaLecturaSinPresionNoDebeRegistrarNada() {
        servicio.registrar("sensor-1", BOCAGRANDE, null, null);

        verify(registrarReporte, never()).registrar(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void elUmbralDebeSerConfigurable() {
        var servicioConUmbralAlto = new RegistrarLecturaDePresionService(sectores, registrarReporte, 30.0, 40.0);

        servicioConUmbralAlto.registrar("sensor-1", BOCAGRANDE, 25.0, null);

        verify(registrarReporte).registrar(eq(BOCAGRANDE), eq(TipoReporte.PRESION_BAJA), any(), any(), eq(true));
    }

    @Test
    void debeRechazarUnSensorSinIdentificador() {
        assertThatThrownBy(() -> servicio.registrar("  ", BOCAGRANDE, 5.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensor");
        assertThatThrownBy(() -> servicio.registrar(null, BOCAGRANDE, 5.0, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(registrarReporte, never()).registrar(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void debeRechazarUnSectorQueNoExiste() {
        SectorId inexistente = new SectorId("no-existe");
        given(sectores.buscarPorId(inexistente)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar("sensor-1", inexistente, 5.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-existe");
        verify(registrarReporte, never()).registrar(any(), any(), any(), any(), anyBoolean());
    }
}
