/**
 * Configuración transversal: pools y transacciones de Mongo, índices, Redis, caché, CORS, tareas asíncronas y programadas,
 * límites de peticiones, OpenAPI y validación de secretos al arrancar.
 *
 * La caché es la abstracción de Spring sobre Redis: cualquier método se cachea con {@code @Cacheable("nombre")} sin tocar este
 * paquete. El TTL por defecto y los específicos por nombre se configuran en application.yml bajo {@code aguavigia.cache}.
 */
package com.aguavigia.ctg.compartido.config;
