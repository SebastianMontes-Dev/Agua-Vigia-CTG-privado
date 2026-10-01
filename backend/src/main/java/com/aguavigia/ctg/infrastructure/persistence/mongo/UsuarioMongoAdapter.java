package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.Consentimiento;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.TipoConsentimiento;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.SecretoTotp;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SegundoFactor;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UsuarioMongoAdapter implements UsuarioRepository {

    private final UsuarioMongoRepository repositorio;

    public UsuarioMongoAdapter(UsuarioMongoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        UsuarioDocumento documento = new UsuarioDocumento();
        documento.setId(usuario.id().valor());
        documento.setCorreo(usuario.correo().normalizado().valor());
        documento.setNombre(usuario.nombre());
        documento.setClaveHash(usuario.claveHash() == null ? null : usuario.claveHash().valor());
        documento.setEstado(usuario.estado().name());
        documento.setRol(usuario.permisos().rol().name());
        documento.setBarrio(usuario.barrio() == null ? null : usuario.barrio().valor());
        documento.setPermisosConcedidos(aNombres(usuario.permisos().concedidos()));
        documento.setPermisosRevocados(aNombres(usuario.permisos().revocados()));
        documento.setSecretoTotp(usuario.segundoFactor() == null
                ? null : usuario.segundoFactor().secreto().valor());
        documento.setSegundoFactorConfirmadoEn(usuario.segundoFactor() == null
                ? null : usuario.segundoFactor().confirmadoEn());
        documento.setCreadoEn(usuario.creadoEn());
        documento.setActualizadoEn(usuario.actualizadoEn());
        documento.setConsentimientos(usuario.consentimientos().stream()
                .map(UsuarioMongoAdapter::aDocumento).toList());
        documento.setBarrioVerificado(usuario.barrioVerificado());
        documento.setBarrioVerificadoEn(usuario.barrioVerificadoEn());

        try {
            repositorio.save(documento);
        } catch (org.springframework.dao.DuplicateKeyException correoRepetido) {
            throw new com.aguavigia.ctg.domain.CorreoYaRegistradoException(documento.getCorreo());
        }
        return usuario;
    }

    @Override
    public Optional<Usuario> buscarPorId(UsuarioId id) {
        return repositorio.findById(id.valor()).map(UsuarioMongoAdapter::aDominio);
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(CorreoElectronico correo) {
        return repositorio.findByCorreo(correo.normalizado().valor()).map(UsuarioMongoAdapter::aDominio);
    }

    @Override
    public boolean existePorCorreo(CorreoElectronico correo) {
        return repositorio.existsByCorreo(correo.normalizado().valor());
    }

    /** Más recientes primero: el panel abre por lo que acaba de llegar, que es lo que hay que atender. */
    @Override
    public Pagina<Usuario> listar(EstadoCuenta filtroEstado, SectorId filtroBarrio, int pagina, int tamano) {
        PageRequest peticion = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "creadoEn"));
        String barrio = filtroBarrio == null ? null : filtroBarrio.valor();
        Page<UsuarioDocumento> resultado;
        if (filtroEstado == null && barrio == null) {
            resultado = repositorio.findAll(peticion);
        } else if (barrio == null) {
            resultado = repositorio.findByEstado(filtroEstado.name(), peticion);
        } else if (filtroEstado == null) {
            resultado = repositorio.findByBarrio(barrio, peticion);
        } else {
            resultado = repositorio.findByEstadoAndBarrio(filtroEstado.name(), barrio, peticion);
        }

        return new Pagina<>(
                resultado.getContent().stream().map(UsuarioMongoAdapter::aDominio).toList(),
                pagina,
                tamano,
                resultado.getTotalElements());
    }

    @Override
    public long contarActivosPorRol(RolVeedor rol) {
        return repositorio.countByRolAndEstado(rol.name(), EstadoCuenta.ACTIVA.name());
    }

    private static List<String> aNombres(Set<Permiso> permisos) {
        return permisos.stream().map(Permiso::name).sorted().toList();
    }

    /**
     * Un permiso que ya no existe en el enum se descarta en vez de reventar la lectura: si una
     * versión futura elimina un Permiso, las cuentas que lo tuvieran concedido deben seguir
     * pudiendo entrar con los que sí existen, no quedar ilegibles.
     */
    private static Set<Permiso> aPermisos(List<String> nombres) {
        if (nombres == null) {
            return Set.of();
        }
        return nombres.stream()
                .map(nombre -> {
                    try {
                        return Permiso.valueOf(nombre);
                    } catch (IllegalArgumentException permisoRetirado) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Usuario aDominio(UsuarioDocumento documento) {
        SegundoFactor segundoFactor = documento.getSecretoTotp() == null
                ? null
                : new SegundoFactor(new SecretoTotp(documento.getSecretoTotp()),
                        documento.getSegundoFactorConfirmadoEn());

        return new Usuario(
                new UsuarioId(documento.getId()),
                new CorreoElectronico(documento.getCorreo()),
                documento.getNombre(),
                documento.getClaveHash() == null ? null : new ClaveHash(documento.getClaveHash()),
                EstadoCuenta.valueOf(documento.getEstado()),
                new PermisosEfectivos(
                        RolVeedor.valueOf(documento.getRol()),
                        aPermisos(documento.getPermisosConcedidos()),
                        aPermisos(documento.getPermisosRevocados())),
                segundoFactor,
                documento.getCreadoEn(),
                documento.getActualizadoEn(),
                documento.getBarrio() == null ? null : new SectorId(documento.getBarrio()),
                documento.getConsentimientos() == null
                        ? List.of()
                        : documento.getConsentimientos().stream().map(UsuarioMongoAdapter::aDominio).toList(),
                documento.isBarrioVerificado(),
                documento.getBarrioVerificadoEn());
    }

    private static UsuarioDocumento.ConsentimientoDocumento aDocumento(Consentimiento consentimiento) {
        UsuarioDocumento.ConsentimientoDocumento documento = new UsuarioDocumento.ConsentimientoDocumento();
        documento.setTipo(consentimiento.tipo().name());
        documento.setVersion(consentimiento.version());
        documento.setFecha(consentimiento.fecha());
        return documento;
    }

    private static Consentimiento aDominio(UsuarioDocumento.ConsentimientoDocumento documento) {
        return new Consentimiento(TipoConsentimiento.valueOf(documento.getTipo()), documento.getVersion(),
                documento.getFecha());
    }
}
