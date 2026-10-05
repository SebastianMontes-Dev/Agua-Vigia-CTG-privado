package com.aguavigia.ctg.infrastructure.persistence.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Document(collection = "usuarios")
public class UsuarioDocumento {

    @Id
    private String id;

    /**
     * Siempre en minúsculas (CorreoElectronico.normalizado()). El índice único es lo que impide de
     * verdad dos cuentas con el mismo correo: comprobarlo antes de insertar deja una ventana entre
     * la lectura y la escritura por la que dos registros simultáneos pasan los dos.
     */
    @Indexed(unique = true)
    private String correo;

    private String nombre;

    /** Hash BCrypt. Nulo mientras la cuenta está INVITADA y aún no fijó clave. */
    private String claveHash;

    private String estado;
    private String rol;

    /** Slug del sector donde vive la persona (ADR-081). Nulo para el ADMIN inicial y las cuentas anteriores. */
    private String barrio;
    private List<String> permisosConcedidos;
    private List<String> permisosRevocados;

    private String secretoTotp;
    private Instant segundoFactorConfirmadoEn;

    private Instant creadoEn;
    private Instant actualizadoEn;

    /** Solo vecinos. Ausente en los documentos anteriores, que se leen como «sin consentimientos». */
    private List<ConsentimientoDocumento> consentimientos;

    /** Solo vecinos: probó con su ubicación que vive en `barrio`. Ausente equivale a falso. */
    private boolean barrioVerificado;
    private Instant barrioVerificadoEn;

    /** Cuenta sintética (D20). Ausente equivale a falso: las cuentas reales no llevan el campo. */
    private boolean datosDeDemostracion;

    /** SEMBRADO para las sintéticas; ausente para las que se registraron de verdad. Lo escribe el adaptador. */
    private String origen;

    @Getter
    @Setter
    public static class ConsentimientoDocumento {
        private String tipo;
        private String version;
        private Instant fecha;
    }
}
