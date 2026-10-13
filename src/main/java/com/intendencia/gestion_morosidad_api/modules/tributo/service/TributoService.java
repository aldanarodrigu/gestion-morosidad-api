package com.intendencia.gestion_morosidad_api.modules.tributo.service;

import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoUpdateRequest;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import java.util.List;
import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoRequest;
import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoYaExisteException;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TributoService {

    private final TributoRepository tributoRepository;

    public List<TributoResponse> listar() {
        return tributoRepository.findAllOrdenados().stream()
                .map(TributoResponse::de)
                .toList();
    }
    @Transactional
    public TributoResponse actualizarDescripcion(Long id, TributoUpdateRequest request) {
        var tributo = tributoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el tributo con id " + id));
        tributo.setDescripcion(request.descripcion().trim());
        return TributoResponse.de(tributoRepository.save(tributo));
    }

    @Transactional
    public TributoResponse crear(TributoRequest request) {
        String codigo = request.codigo().trim();
        if (tributoRepository.findByCodigo(codigo).isPresent()) {
            throw new RecursoYaExisteException("Ya existe un tributo con el codigo " + codigo);
        }
        Tributo tributo = new Tributo();
        tributo.setCodigo(codigo);
        tributo.setDescripcion(request.descripcion().trim());
        try {
            return TributoResponse.de(tributoRepository.saveAndFlush(tributo));
        } catch (DataIntegrityViolationException ex) {
            throw new RecursoYaExisteException("Ya existe un tributo con el codigo " + codigo);
        }
    }

    @Transactional
    public void eliminar(Long id) {
        var tributo = tributoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el tributo con id " + id));
        if (tributoRepository.tieneDeudas(id)) {
            throw new ReglaNegocioException("No se puede eliminar un tributo asociado a deudas.");
        }
        if (tributoRepository.tieneConfiguraciones(id))
            throw new ReglaNegocioException("No se puede eliminar un tributo con historial de configuraciones de mora.");
        try {
            tributoRepository.delete(tributo);
            tributoRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ReglaNegocioException("No se puede eliminar un tributo asociado a deudas.");
        }
    }

}
