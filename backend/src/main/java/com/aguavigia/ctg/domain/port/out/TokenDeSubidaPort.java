package com.aguavigia.ctg.domain.port.out;

/** Fabrica los tokens de subida y los resume; el token en claro solo existe entre que se emite y que se usa. */
public interface TokenDeSubidaPort {

    /** Un token nuevo, impredecible y seguro para una cabecera HTTP. */
    String nuevo();

    /** El resumen con que se guarda y se compara: de él no se puede recuperar el token. */
    String hash(String tokenEnClaro);
}
