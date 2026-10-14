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

    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");
    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private RedDeOrigen() {
    }

    /** La red a efectos de límites y de quórum: la IPv4 tal cual y el prefijo /64 de una IPv6. */
    public static String de(String ip) {
        if (ip == null || !ip.contains(":")) {
            return ip;
        }
        InetAddress direccion = literal(ip);
        if (direccion == null) {
            return ip;
        }
        return direccion instanceof Inet6Address ? prefijo64(direccion) : direccion.getHostAddress();
    }

    /**
     * Lo que se guarda de una IP en la auditoría de la ciudadanía: el bloque, no la dirección. Una IPv4 queda en
     * su /24 y una IPv6 en su /64; sirve para investigar un abuso sin acumular la IP de cada vecino.
     */
    public static String aproximada(String ip) {
        InetAddress direccion = literal(ip);
        if (direccion == null) {
            return ip;
        }
        if (direccion instanceof Inet6Address) {
            return prefijo64(direccion);
        }
        byte[] octetos = direccion.getAddress();
        return (octetos[0] & 0xff) + "." + (octetos[1] & 0xff) + "." + (octetos[2] & 0xff) + ".0/24";
    }

    private static String prefijo64(InetAddress direccion) {
        byte[] bytes = direccion.getAddress();
        return String.format("%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64",
                bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7]);
    }

    /** La dirección si el texto es una IP literal; nunca resuelve un nombre. */
    private static InetAddress literal(String ip) {
        if (ip == null) {
            return null;
        }
        String sinZona = ip.contains("%") ? ip.substring(0, ip.indexOf('%')) : ip;
        boolean esLiteral = sinZona.contains(":")
                ? IPV6_LITERAL.matcher(sinZona).matches()
                : IPV4_LITERAL.matcher(sinZona).matches();
        if (!esLiteral) {
            return null;
        }
        try {
            return InetAddress.getByName(sinZona);
        } catch (UnknownHostException noEsUnaIp) {
            return null;
        }
    }
}
