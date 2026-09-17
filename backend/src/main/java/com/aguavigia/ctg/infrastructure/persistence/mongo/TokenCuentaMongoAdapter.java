package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.TokenCuenta;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.TokenCuentaRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TokenCuentaMongoAdapter implements TokenCuentaRepository {

    private final TokenCuentaMongoRepository repositorio;
    private final RelojPort reloj;
    private final MongoTemplate mongoTemplate;

    public TokenCuentaMongoAdapter(TokenCuentaMongoRepository repositorio, RelojPort reloj,
                                    MongoTemplate mongoTemplate) {
        this.repositorio = repositorio;
        this.reloj = reloj;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public TokenCuenta guardar(TokenCuenta token) {
        TokenCuentaDocumento documento = new TokenCuentaDocumento();
        documento.setHash(token.hash());
        documento.setTipo(token.tipo().name());
        documento.setUsuarioId(token.usuarioId().valor());
        documento.setCreadoEn(token.creadoEn());
        documento.setUsadoEn(token.usadoEn());
        documento.setExpiraEn(token.venceEn());

        repositorio.save(documento);
        return token;
    }

    @Override
    public Optional<TokenCuenta> buscarPorHash(String hash) {
        return repositorio.findById(hash).map(TokenCuentaMongoAdapter::aDominio);
    }

    /**
     * Se marcan como usados en vez de borrarlos: si alguien abre el enlace viejo, el sistema puede
     * decirle "este enlace ya no vale" en vez de "no existe", que es lo que verá si el documento
     * desapareció. Y el rastro queda para la auditoría.
     */
    @Override
    public void invalidarVigentes(UsuarioId usuarioId, TipoTokenCuenta tipo) {
        Query query = Query.query(Criteria.where("usuarioId").is(usuarioId.valor())
                .and("tipo").is(tipo.name())
                .and("usadoEn").isNull());
        Update update = Update.update("usadoEn", reloj.ahora());
        mongoTemplate.updateMulti(query, update, TokenCuentaDocumento.class);
    }

    private static TokenCuenta aDominio(TokenCuentaDocumento documento) {
        return new TokenCuenta(
                documento.getHash(),
                TipoTokenCuenta.valueOf(documento.getTipo()),
                new UsuarioId(documento.getUsuarioId()),
                documento.getCreadoEn(),
                documento.getUsadoEn());
    }
}
