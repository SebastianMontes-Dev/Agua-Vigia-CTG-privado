package com.aguavigia.ctg.domain.port.out;

/** Secretos que el propio sistema genera y conserva (no los que pone quien despliega). */
public interface SecretosDelSistemaPort {

    /**
     * El secreto de ese nombre; si no existe, lo genera al azar y lo guarda. Dos llamadas, aunque sean
     * simultáneas o de instancias distintas, devuelven siempre el mismo valor.
     */
    String obtenerOCrear(String nombre);
}
