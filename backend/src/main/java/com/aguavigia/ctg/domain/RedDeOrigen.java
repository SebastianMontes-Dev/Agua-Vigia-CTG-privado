package com.aguavigia.ctg.domain;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Qué cuenta como «la misma red» para limitar y para componer el quórum.
 *
 * Una IPv4 es la propia dirección. A un abonado IPv6 le entregan un /64 entero (2^64 direcciones), así que si cada
 * dirección fuera una red, rotar dentro de su prefijo fabricaría redes distintas y cupos nuevos sin límite: se
 * usa solo el prefijo de 64 bits.
 *
 * Solo se interpreta una IP literal; cualquier otro texto se devuelve igual, para no consultar nunca un DNS.
 */
public final class RedDeOrigen {

    private static final Pattern IP_LITERAL_CON_DOS_PUNTOS = Pattern.compile("[0-9a-fA-F:.]+");

    private RedDeOrigen() {
    }

    public static String de(String ip) {
        if (ip == null || !ip.contains(":")) {
            return ip;
        }
        String sinZona = ip.contains("%") ? ip.substring(0, ip.indexOf('%')) : ip;
        if (!IP_LITERAL_CON_DOS_PUNTOS.matcher(sinZona).matches()) {
            return ip;
        }
        try {
            InetAddress direccion = InetAddress.getByName(sinZona);
            if (direccion instanceof Inet6Address) {
                byte[] bytes = direccion.getAddress();
                return String.format("%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64",
                        bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7]);
            }
            return direccion.getHostAddress();
        } catch (UnknownHostException noEsUnaIp) {
            return ip;
        }
    }
}
