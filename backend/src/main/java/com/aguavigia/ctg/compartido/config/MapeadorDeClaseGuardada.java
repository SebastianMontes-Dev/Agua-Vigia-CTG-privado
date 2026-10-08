package com.aguavigia.ctg.compartido.config;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.convert.TypeInformationMapper;
import org.springframework.data.mapping.Alias;
import org.springframework.data.util.TypeInformation;

/**
 * Escribe en {@code _class} el nombre que fija {@link ClaseGuardadaComo}. Si la clase no lo lleva, no opina y el mapeador de
 * siempre (el nombre completo de la clase) decide. No resuelve nombres al leer: de eso se ocupa ese otro mapeador, y un nombre
 * que ya no existe se lee como el tipo declarado.
 */
public class MapeadorDeClaseGuardada implements TypeInformationMapper {

    @Override
    public Alias createAliasFor(TypeInformation<?> tipo) {
        ClaseGuardadaComo fijado = AnnotationUtils.findAnnotation(tipo.getType(), ClaseGuardadaComo.class);
        return fijado == null ? Alias.NONE : Alias.of(fijado.value());
    }

    @Override
    public TypeInformation<?> resolveTypeFrom(Alias alias) {
        return null;
    }
}
