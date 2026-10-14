package com.aguavigia.ctg.domain.port.out;

/**
 * Da de alta a un cliente en el canal en vivo de los sectores. El tipo de la conexión es un parámetro porque el
 * dominio no conoce el transporte (hoy SSE de Spring MVC): quien lo usa declara qué recibe, y quien lo implementa,
 * qué entrega. Así la capa de API depende de este puerto y no de la clase concreta de infraestructura.
 *
 * @param <C> la conexión que se devuelve al cliente y que el servidor mantiene abierta
 */
public interface CanalEnVivoPort<C> {

    C registrar();
}
