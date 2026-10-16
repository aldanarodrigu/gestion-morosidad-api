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
import java.util.Objects;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.PeriodosMoraRequest;
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
    private final PadronRepository padronRepository;

    public List<String> listarTiposPadron() {
        var tipos = new java.util.TreeSet<>(padronRepository.listarTiposPadron());
        configuracionRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(ConfiguracionSegmento::getTipoPadron).filter(Objects::nonNull).forEach(tipos::add);
        return List.copyOf(tipos);
    }

    public List<ConfiguracionSegmentoResponse> listar(boolean historial) {
        return listar(historial, null);
    }

    public List<ConfiguracionSegmentoResponse> listar(boolean historial, String tipoPadron) {
        String tipo = validarTipoPadron(tipoPadron);
        var todas = historial ? configuracionRepository.findAllByOrderByFechaCreacionDesc()
                : configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc();
        var propias = todas.stream().filter(c -> esDelTipo(c, tipo)).toList();
        if (!historial && tipo != null && propias.isEmpty())
            propias = todas.stream().filter(c -> esDelTipo(c, null)).toList();
        return propias.stream().map(ConfiguracionSegmentoResponse::de).toList();
    }

    private static boolean esDelTipo(ConfiguracionSegmento c, String tipoPadron) {
        return c.getTributo() == null && Objects.equals(c.getTipoPadron(), tipoPadron);
    }

    private String validarTipoPadron(String tipoPadron) {
        if (tipoPadron == null) return null;
        String tipo = tipoPadron.trim();
        if (tipo.isEmpty() || tipo.length() > 100)
            throw new ReglaNegocioException("Debe indicarse un PadTipo válido.");
        if (!listarTiposPadron().contains(tipo))
            throw new RecursoNoEncontradoException("No existe el PadTipo " + tipo);
        return tipo;
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

        List<ConfiguracionSegmento> activas = configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc()
                .stream().filter(c -> esDelTipo(c, null)).toList();
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

    /** Reemplaza todos los rangos de forma atomica, sin huecos ni solapamientos. */
    @Transactional
    public List<ConfiguracionSegmentoResponse> actualizarPeriodos(PeriodosMoraRequest request) {
        String tipoPadron = validarTipoPadron(request.tipoPadron());
        var segmentos = segmentoMoraRepository.findAllByOrderByOrdenAsc();
        var porId = segmentos.stream().collect(Collectors.toMap(SegmentoMora::getId, s -> s));
        var recibidos = new HashSet<Long>();
        for (var rango : request.rangos()) {
            if (!porId.containsKey(rango.segmentoId()))
                throw new RecursoNoEncontradoException("No existe el segmento con id " + rango.segmentoId());
            if (!recibidos.add(rango.segmentoId()))
                throw new ReglaNegocioException("Cada segmento debe aparecer una sola vez.");
        }
        if (!recibidos.equals(porId.keySet()))
            throw new ReglaNegocioException("Deben indicarse los rangos de todos los segmentos.");
        var solicitudes = request.rangos().stream()
                .collect(Collectors.toMap(ConfiguracionSegmentoRequest::segmentoId, r -> r));
        var nuevas = new ArrayList<ConfiguracionSegmento>();
        long siguiente = 0;
        for (int i = 0; i < segmentos.size(); i++) {
            var segmento = segmentos.get(i);
            var rango = solicitudes.get(segmento.getId());
            boolean ultimo = i == segmentos.size() - 1;
            if (rango.diasDesde() != siguiente || rango.diasDesde() < 0)
                throw new ReglaNegocioException("Los periodos deben ser consecutivos y comenzar en 0 dias.");
            if (ultimo != (rango.diasHasta() == null))
                throw new ReglaNegocioException("Solo el ultimo segmento debe quedar sin limite superior.");
            if (rango.diasHasta() != null && rango.diasHasta() < rango.diasDesde())
                throw new ReglaNegocioException("El limite superior no puede ser menor al inicio del periodo.");
            var nueva = new ConfiguracionSegmento();
            nueva.setSegmento(segmento);
            nueva.setTipoPadron(tipoPadron);
            nueva.setDiasDesde(rango.diasDesde());
            nueva.setDiasHasta(rango.diasHasta());
            nuevas.add(nueva);
            if (!ultimo) siguiente = (long) rango.diasHasta() + 1;
        }
        var activas = configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc()
                .stream().filter(c -> esDelTipo(c, tipoPadron)).toList();
        activas.forEach(c -> c.setActiva(false));
        configuracionRepository.saveAll(activas);
        configuracionRepository.flush();
        var guardadas = configuracionRepository.saveAllAndFlush(nuevas);
        eventPublisher.publishEvent(new ConfiguracionSegmentoCambiadaEvent(tipoPadron));
        return guardadas.stream().map(ConfiguracionSegmentoResponse::de).toList();
    }

    @Transactional
    public void restaurarBase(String tipoPadron) {
        String tipo = validarTipoPadron(tipoPadron);
        if (tipo == null) throw new ReglaNegocioException("Debe indicarse un PadTipo.");
        var propias = configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc().stream()
                .filter(c -> esDelTipo(c, tipo)).toList();
        if (propias.isEmpty()) return;
        propias.forEach(c -> c.setActiva(false));
        configuracionRepository.saveAllAndFlush(propias);
        eventPublisher.publishEvent(new ConfiguracionSegmentoCambiadaEvent(tipo));
    }

    private static String describir(ConfiguracionSegmento configuracion) {
        return configuracion.getDiasDesde() + " a "
                + (configuracion.getDiasHasta() == null ? "sin tope" : configuracion.getDiasHasta()) + " días";
    }
}
