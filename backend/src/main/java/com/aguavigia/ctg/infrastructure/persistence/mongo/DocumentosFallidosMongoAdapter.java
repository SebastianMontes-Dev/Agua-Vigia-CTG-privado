package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.DocumentoFallido;
import com.aguavigia.ctg.domain.port.out.DocumentosFallidosPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentosFallidosMongoAdapter implements DocumentosFallidosPort {

    private final DocumentoFallidoMongoRepository repositorio;

    public DocumentosFallidosMongoAdapter(DocumentoFallidoMongoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public List<DocumentoFallido> masRecientes() {
        return repositorio.findTop200ByOrderByUltimoIntentoDesc().stream()
                .map(documento -> new DocumentoFallido(
                        documento.getFuente(),
                        documento.getUrlOriginal(),
                        documento.getTitulo(),
                        documento.getMotivo(),
                        documento.getPrimerIntento(),
                        documento.getUltimoIntento(),
                        documento.getReintentos()))
                .toList();
    }
}
