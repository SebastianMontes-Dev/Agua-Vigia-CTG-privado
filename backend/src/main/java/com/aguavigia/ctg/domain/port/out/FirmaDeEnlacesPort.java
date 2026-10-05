package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.EnlaceDeRestablecimiento;

import java.util.Optional;

/** Firma los enlaces de un toque que van en los correos: el cliente no puede fabricar uno ni cambiar el de otro. */
public interface FirmaDeEnlacesPort {

    /** El token que va en la URL del correo. Solo lleva lo que el enlace declara y su firma. */
    String emitir(EnlaceDeRestablecimiento enlace);

    /** Vacío si el token está mal formado o su firma no corresponde a este servidor. No mira el vencimiento. */
    Optional<EnlaceDeRestablecimiento> verificar(String token);
}
