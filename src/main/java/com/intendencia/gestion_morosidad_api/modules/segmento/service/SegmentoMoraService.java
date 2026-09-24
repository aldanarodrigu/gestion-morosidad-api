package com.intendencia.gestion_morosidad_api.modules.segmento.service;

import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraRequest;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoYaExisteException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SegmentoMoraService {

    private final SegmentoMoraRepository segmentoMoraRepository;

    public List<SegmentoMoraResponse> listar() {
        return segmentoMoraRepository.findAllByOrderByOrdenAsc().stream()
                .map(SegmentoMoraResponse::de)
                .toList();
    }

    /** Crea un segmento sin rango: se le asigna uno con POST /configuraciones-segmento. */
    @Transactional
    public SegmentoMoraResponse crear(SegmentoMoraRequest request) {
        String nombre = request.nombre().trim();
        if (segmentoMoraRepository.existsByNombreIgnoreCase(nombre)) {
            throw new RecursoYaExisteException("Ya existe un segmento con el nombre '" + nombre + "'");
        }
        SegmentoMora segmento = new SegmentoMora();
        segmento.setNombre(nombre);
        segmento.setDescripcion(request.descripcion());
        segmento.setOrden(request.orden());
        SegmentoMora guardado = segmentoMoraRepository.save(segmento);
        log.info("Segmento de mora creado: id={} nombre={}", guardado.getId(), guardado.getNombre());
        return SegmentoMoraResponse.de(guardado);
    }
}
