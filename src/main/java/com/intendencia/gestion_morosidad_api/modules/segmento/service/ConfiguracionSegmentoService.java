package com.intendencia.gestion_morosidad_api.modules.segmento.service;

import com.intendencia.gestion_morosidad_api.modules.segmento.dto.ConfiguracionSegmentoRequest;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.ConfiguracionSegmentoResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.ConfiguracionSegmento;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.ConfiguracionSegmentoRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConfiguracionSegmentoService {

    private final ConfiguracionSegmentoRepository configuracionRepository;
    private final SegmentoMoraRepository segmentoMoraRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** @param historial true = incluye las configuraciones reemplazadas */
    public List<ConfiguracionSegmentoResponse> listar(boolean historial) {
        List<ConfiguracionSegmento> configuraciones = historial
                ? configuracionRepository.findAllByOrderByFechaCreacionDesc()
                : configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc();
        return configuraciones.stream().map(ConfiguracionSegmentoResponse::de).toList();
    }

    public ReglasSegmentacion reglasVigentes() {
        return new ReglasSegmentacion(configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc());
    }

    /**
     * Asigna un nuevo rango a un segmento: desactiva el rango activo anterior (queda en el historial)
     * y verifica que el nuevo no se superponga con el de otro segmento.
     * Después se recalculan los segmentos de todas las deudas.
     */
    @Transactional
    public ConfiguracionSegmentoResponse crear(ConfiguracionSegmentoRequest request) {
        SegmentoMora segmento = segmentoMoraRepository.findById(request.segmentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el segmento de mora con id " + request.segmentoId()));

        ConfiguracionSegmento nueva = new ConfiguracionSegmento();
        nueva.setSegmento(segmento);
        nueva.setDiasDesde(request.diasDesde());
        nueva.setDiasHasta(request.diasHasta());

        if (nueva.getDiasHasta() != null && nueva.getDiasHasta() < nueva.getDiasDesde()) {
            throw new ReglaNegocioException("diasHasta debe ser mayor o igual a diasDesde");
        }

        List<ConfiguracionSegmento> activas = configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc();
        for (ConfiguracionSegmento activa : activas) {
            boolean esDelMismoSegmento = activa.getSegmento().getId().equals(segmento.getId());
            if (!esDelMismoSegmento && nueva.seSuperponeCon(activa)) {
                throw new ReglaNegocioException("El rango se superpone con el de '"
                        + activa.getSegmento().getNombre() + "' (" + describir(activa) + ")");
            }
        }

        // Flush antes de insertar: Hibernate ejecuta los INSERT antes que los UPDATE y el índice
        // único de "una configuración activa por segmento" rechazaría la nueva.
        activas.stream()
                .filter(activa -> activa.getSegmento().getId().equals(segmento.getId()))
                .forEach(anterior -> {
                    anterior.setActiva(false);
                    configuracionRepository.saveAndFlush(anterior);
                });

        ConfiguracionSegmento guardada = configuracionRepository.save(nueva);
        log.info("Rango del segmento '{}' actualizado a {}", segmento.getNombre(), describir(guardada));

        eventPublisher.publishEvent(new ConfiguracionSegmentoCambiadaEvent());
        return ConfiguracionSegmentoResponse.de(guardada);
    }

    private static String describir(ConfiguracionSegmento configuracion) {
        return configuracion.getDiasDesde() + " a "
                + (configuracion.getDiasHasta() == null ? "sin tope" : configuracion.getDiasHasta()) + " días";
    }
}
