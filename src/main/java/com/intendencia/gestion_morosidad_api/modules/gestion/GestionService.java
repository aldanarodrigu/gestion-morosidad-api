package com.intendencia.gestion_morosidad_api.modules.gestion;

import com.intendencia.gestion_morosidad_api.modules.accion.Accion;
import com.intendencia.gestion_morosidad_api.modules.accion.AccionRepository;
import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionResponse;
import com.intendencia.gestion_morosidad_api.modules.accion_segmento.AccionSegmento;
import com.intendencia.gestion_morosidad_api.modules.accion_segmento.AccionSegmentoRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.gestion.dto.GestionRequest;
import com.intendencia.gestion_morosidad_api.modules.gestion.dto.GestionResponse;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GestionService {

    private final GestionRepository gestionRepository;
    private final DeudaRepository deudaRepository;
    private final AccionRepository accionRepository;
    private final AccionSegmentoRepository accionSegmentoRepository;

    public GestionService(
            GestionRepository gestionRepository,
            DeudaRepository deudaRepository,
            AccionRepository accionRepository,
            AccionSegmentoRepository accionSegmentoRepository
    ) {
        this.gestionRepository = gestionRepository;
        this.deudaRepository = deudaRepository;
        this.accionRepository = accionRepository;
        this.accionSegmentoRepository = accionSegmentoRepository;
    }

    @Transactional(readOnly = true)
    public List<GestionResponse> getAllGestiones() {
        return gestionRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GestionResponse getGestionById(Long id) {

        Gestion gestion = gestionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la gestión con id: " + id
                ));

        return mapToResponse(gestion);
    }

    @Transactional
    public GestionResponse createGestion(GestionRequest request) {

        Deuda deuda = deudaRepository.findById(request.deudaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la deuda con id: " + request.deudaId()
                ));

        Accion accion = accionRepository.findById(request.accionId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la acción con id: " + request.accionId()
                ));

        Gestion gestion = new Gestion();

        gestion.setFecha(request.fecha());
        gestion.setResultado(request.resultado());
        gestion.setObservacion(request.observacion());
        gestion.setDeuda(deuda);
        gestion.setAccion(accion);

        Gestion guardada = gestionRepository.save(gestion);

        return mapToResponse(guardada);
    }

    @Transactional(readOnly = true)
    public List<AccionResponse> getAccionesDisponibles(Long deudaId) {

        Deuda deuda = deudaRepository.findById(deudaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la deuda con id: " + deudaId
                ));

        Long segmentoId = deuda.getSegmentoMora().getId();

        return accionSegmentoRepository
                .findBySegmentoMora_Id(segmentoId)
                .stream()
                .map(AccionSegmento::getAccion)
                .filter(Accion::getActivo)
                .map(accion -> new AccionResponse(
                        accion.getId(),
                        accion.getNombre(),
                        accion.getActivo()
                ))
                .toList();
    }

    private GestionResponse mapToResponse(Gestion gestion) {

        return new GestionResponse(
                gestion.getId(),
                gestion.getFecha(),
                gestion.getResultado(),
                gestion.getObservacion(),
                gestion.getDeuda().getId(),
                gestion.getAccion().getId(),
                gestion.getAccion().getNombre()
        );
    }
}