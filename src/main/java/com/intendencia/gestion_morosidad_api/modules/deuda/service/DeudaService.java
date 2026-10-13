package com.intendencia.gestion_morosidad_api.modules.deuda.service;

import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionResponse;
import com.intendencia.gestion_morosidad_api.modules.accion_segmento.AccionSegmentoRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.DeudaFiltro;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.DeudaResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.mapper.DeudaMapper;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaSpecifications;
import com.intendencia.gestion_morosidad_api.modules.gestion.GestionRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoCambiadaEvent;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ReglasSegmentacion;
import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DeudaService {

    private final DeudaRepository deudaRepository;
    private final ConfiguracionSegmentoService configuracionSegmentoService;
    private final Clock clock;
    private final AccionSegmentoRepository accionSegmentoRepository;
    private final GestionRepository gestionRepository;

    public PaginaResponse<DeudaResponse> listar(
            DeudaFiltro filtro,
            Pageable pageable
    ) {
        var pagina = deudaRepository.findAll(DeudaSpecifications.conFiltro(filtro), pageable);
        return new PaginaResponse<>(mapearDeudas(pagina.getContent()), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    /** Enriquecimiento compartido para la cartera individual y agrupada, en consultas por lote. */
    List<DeudaResponse> mapearDeudas(List<Deuda> deudas) {
        LocalDate hoy = LocalDate.now(clock);
        List<Long> segmentoIds = deudas.stream().map(Deuda::getSegmentoMora)
                .filter(java.util.Objects::nonNull).map(s -> s.getId()).distinct().toList();
        var acciones = obtenerAccionesPorSegmento(segmentoIds);
        var gestiones = contarGestiones(deudas.stream().map(Deuda::getId).toList());
        return deudas.stream().map(deuda -> DeudaMapper.toResponse(deuda, hoy,
                        deuda.getSegmentoMora() == null ? List.of()
                                : acciones.getOrDefault(deuda.getSegmentoMora().getId(), List.of()))
                .toBuilder().cantidadGestiones(gestiones.getOrDefault(deuda.getId(), 0L)).build()).toList();
    }

    public DeudaResponse obtener(Long id) {
        Deuda deuda = buscar(id);
        LocalDate hoy = LocalDate.now(clock);

        List<AccionResponse> acciones =
                obtenerAccionesDeuda(deuda);

        return DeudaMapper.toResponse(deuda, hoy, acciones);
    }

    public List<TributoResponse> listarTributos(Long id) {
        return DeudaMapper.toTributosResponse(buscar(id));
    }

    public List<DeudaResponse> listarPorPadron(String numeroPadron) {
        LocalDate hoy = LocalDate.now(clock);

        List<Deuda> deudas = deudaRepository
                .findByPadronNumeroPadronOrderByDeudaDesdeAsc(
                        numeroPadron.trim()
                );

        List<Long> segmentoIds = deudas.stream()
                .map(Deuda::getSegmentoMora)
                .filter(segmento -> segmento != null)
                .map(segmento -> segmento.getId())
                .distinct()
                .toList();

        Map<Long, List<AccionResponse>> accionesPorSegmento =
                obtenerAccionesPorSegmento(segmentoIds);

        return deudas.stream()
                .map(deuda -> {
                    Long segmentoId = deuda.getSegmentoMora() == null
                            ? null
                            : deuda.getSegmentoMora().getId();

                    List<AccionResponse> acciones = segmentoId == null
                            ? List.of()
                            : accionesPorSegmento.getOrDefault(
                            segmentoId,
                            List.of()
                    );

                    return DeudaMapper.toResponse(
                            deuda,
                            hoy,
                            acciones
                    );
                })
                .toList();
    }

    @Transactional
    public DeudaResponse actualizarEstado(
            Long id,
            EstadoDeuda nuevoEstado
    ) {
        Deuda deuda = buscar(id);
        EstadoDeuda anterior = deuda.getEstado();

        deuda.setEstado(nuevoEstado);

        log.info(
                "Estado de deuda {} actualizado: {} -> {}",
                id,
                anterior,
                nuevoEstado
        );

        Deuda deudaActualizada = deudaRepository.save(deuda);

        return DeudaMapper.toResponse(
                deudaActualizada,
                LocalDate.now(clock),
                obtenerAccionesDeuda(deudaActualizada)
        );
    }

    /**
     * Recalcula el segmento de todas las deudas no canceladas
     * cuando el administrador cambia los rangos.
     */
    @EventListener
    @Transactional
    public void recalcularSegmentos(
            ConfiguracionSegmentoCambiadaEvent evento
    ) {
        ReglasSegmentacion reglas =
                configuracionSegmentoService.reglasVigentes();

        LocalDate hoy = LocalDate.now(clock);

        List<Deuda> deudas =
                deudaRepository.findByEstadoNot(EstadoDeuda.CANCELADA);

        deudas.forEach(deuda ->
                deuda.setSegmentoMora(
                        reglas.segmentoPara(deuda.getDeudaDesde(), hoy, deuda.getTributos())
                )
        );

        log.info(
                "Segmentos recalculados para {} deudas",
                deudas.size()
        );
    }

    public List<DeudaResponse> listarPorCm(String cm) {
        LocalDate hoy = LocalDate.now(clock);

        List<Deuda> deudas =
                deudaRepository.findByPadronCmOrderByDeudaDesdeAsc(cm.trim());

        List<Long> segmentoIds = deudas.stream()
                .map(Deuda::getSegmentoMora)
                .filter(segmento -> segmento != null)
                .map(segmento -> segmento.getId())
                .distinct()
                .toList();

        Map<Long, List<AccionResponse>> accionesPorSegmento =
                obtenerAccionesPorSegmento(segmentoIds);

        Map<Long, Long> gestionesPorDeuda = contarGestiones(
                deudas.stream().map(Deuda::getId).toList()
        );

        return deudas.stream()
                .map(deuda -> {
                    Long segmentoId = deuda.getSegmentoMora() == null
                            ? null
                            : deuda.getSegmentoMora().getId();

                    List<AccionResponse> acciones = segmentoId == null
                            ? List.of()
                            : accionesPorSegmento.getOrDefault(
                            segmentoId,
                            List.of()
                    );

                    return DeudaMapper.toResponse(deuda, hoy, acciones)
                            .toBuilder()
                            .cantidadGestiones(
                                    gestionesPorDeuda.getOrDefault(
                                            deuda.getId(), 0L
                                    )
                            )
                            .build();
                })
                .toList();
    }

    public List<DeudaResponse> listarPorContribuyenteId(Long contribuyenteId) {
        LocalDate hoy = LocalDate.now(clock);

        List<Deuda> deudas = deudaRepository
                .findByPadron_Contribuyente_IdOrderByDeudaDesdeAsc(
                        contribuyenteId
                );

        List<Long> segmentoIds = deudas.stream()
                .map(Deuda::getSegmentoMora)
                .filter(segmento -> segmento != null)
                .map(segmento -> segmento.getId())
                .distinct()
                .toList();

        Map<Long, List<AccionResponse>> accionesPorSegmento =
                obtenerAccionesPorSegmento(segmentoIds);

        Map<Long, Long> gestionesPorDeuda = contarGestiones(
                deudas.stream().map(Deuda::getId).toList()
        );

        return deudas.stream()
                .map(deuda -> {
                    Long segmentoId = deuda.getSegmentoMora() == null
                            ? null
                            : deuda.getSegmentoMora().getId();

                    List<AccionResponse> acciones = segmentoId == null
                            ? List.of()
                            : accionesPorSegmento.getOrDefault(
                            segmentoId, List.of()
                    );

                    return DeudaMapper.toResponse(deuda, hoy, acciones)
                            .toBuilder()
                            .cantidadGestiones(
                                    gestionesPorDeuda.getOrDefault(
                                            deuda.getId(), 0L
                                    )
                            )
                            .build();
                })
                .toList();
    }

    private List<AccionResponse> obtenerAccionesDeuda(Deuda deuda) {
        if (deuda.getSegmentoMora() == null) {
            return List.of();
        }

        Long segmentoId = deuda.getSegmentoMora().getId();

        return obtenerAccionesPorSegmento(List.of(segmentoId))
                .getOrDefault(segmentoId, List.of());
    }

    private Map<Long, List<AccionResponse>> obtenerAccionesPorSegmento(
            Collection<Long> segmentoIds
    ) {
        if (segmentoIds.isEmpty()) {
            return Map.of();
        }

        return accionSegmentoRepository
                .findBySegmentoMora_IdIn(segmentoIds)
                .stream()
                .filter(as -> as.getAccion().getActivo())
                .collect(Collectors.groupingBy(
                        as -> as.getSegmentoMora().getId(),
                        Collectors.mapping(
                                as -> new AccionResponse(
                                        as.getAccion().getId(),
                                        as.getAccion().getNombre(),
                                        as.getAccion().getActivo()
                                ),
                                Collectors.toList()
                        )
                ));
    }

    private Deuda buscar(Long id) {
        return deudaRepository.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "No existe la deuda con id " + id
                        )
                );
    }

    private Map<Long, Long> contarGestiones(List<Long> deudaIds) {
        if (deudaIds.isEmpty()) {
            return Map.of();
        }

        return gestionRepository.contarPorDeudaIds(deudaIds)
                .stream()
                .collect(Collectors.toMap(
                        fila -> (Long) fila[0],
                        fila -> (Long) fila[1]
                ));
    }
}