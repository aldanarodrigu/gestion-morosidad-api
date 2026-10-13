package com.intendencia.gestion_morosidad_api.modules.accion;

import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionRequest;
import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionResponse;
import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionUpdateRequest;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoYaExisteException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.intendencia.gestion_morosidad_api.modules.accion_segmento.AccionSegmento;
import com.intendencia.gestion_morosidad_api.modules.accion_segmento.AccionSegmentoRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccionService {

    private final AccionRepository accionRepository;

    private final AccionSegmentoRepository accionSegmentoRepository;
    private final SegmentoMoraRepository segmentoMoraRepository;

    public List<AccionResponse> getAllAcciones() {
        var porAccion = accionSegmentoRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(v -> v.getAccion().getId(),
                        Collectors.mapping(v -> SegmentoMoraResponse.de(v.getSegmentoMora()), Collectors.toList())));
        return accionRepository.findAll().stream()
                .map(a -> new AccionResponse(a.getId(), a.getNombre(), a.getActivo(),
                        porAccion.getOrDefault(a.getId(), List.of())))
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

        var segmento = segmentoMoraRepository.findById(request.segmentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el segmento de mora indicado"));
        String nombre = request.nombre().trim();
        if (accionRepository.existsByNombreIgnoreCase(nombre)) {
            throw new RecursoYaExisteException(
                    "Ya existe una acción con el nombre: " + request.nombre()
            );
        }

        Accion accion = new Accion();
        accion.setNombre(nombre);
        accion.setActivo(true);

        Accion accionGuardada = accionRepository.save(accion);
        AccionSegmento vinculo = new AccionSegmento();
        vinculo.setAccion(accionGuardada);
        vinculo.setSegmentoMora(segmento);
        accionSegmentoRepository.save(vinculo);
        return new AccionResponse(accionGuardada.getId(), accionGuardada.getNombre(),
                accionGuardada.getActivo(), List.of(SegmentoMoraResponse.de(segmento)));
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
                accion.getActivo(),
                accionSegmentoRepository.findByAccion_Id(accion.getId()).stream()
                        .map(v -> SegmentoMoraResponse.de(v.getSegmentoMora())).toList()
        );
    }
}