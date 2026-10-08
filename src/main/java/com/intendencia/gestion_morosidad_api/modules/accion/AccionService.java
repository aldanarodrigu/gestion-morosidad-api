package com.intendencia.gestion_morosidad_api.modules.accion;

import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionRequest;
import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionResponse;
import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionUpdateRequest;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoYaExisteException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccionService {

    private final AccionRepository accionRepository;

    public AccionService(AccionRepository accionRepository) {
        this.accionRepository = accionRepository;
    }

    public List<AccionResponse> getAllAcciones() {
        return accionRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public AccionResponse getAccionById(Long id) {

        Accion accion = accionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la acción con id: " + id
                ));

        return mapToResponse(accion);
    }

    @Transactional
    public AccionResponse createAccion(AccionRequest request) {

        if (accionRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new RecursoYaExisteException(
                    "Ya existe una acción con el nombre: " + request.nombre()
            );
        }

        Accion accion = new Accion();
        accion.setNombre(request.nombre());
        accion.setActivo(true);

        Accion accionGuardada = accionRepository.save(accion);

        return mapToResponse(accionGuardada);
    }

    @Transactional
    public AccionResponse updateEstado(
            Long id,
            AccionUpdateRequest request) {

        Accion accion = accionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la acción con id: " + id
                ));

        accion.setActivo(request.activo());

        return mapToResponse(accionRepository.save(accion));
    }

    @Transactional
    public void deleteAccion(Long id) {

        Accion accion = accionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la acción con id: " + id
                ));

        accionRepository.delete(accion);
    }

    private AccionResponse mapToResponse(Accion accion) {
        return new AccionResponse(
                accion.getId(),
                accion.getNombre(),
                accion.getActivo()
        );
    }
}