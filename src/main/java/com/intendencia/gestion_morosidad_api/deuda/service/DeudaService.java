package com.intendencia.gestion_morosidad_api.deuda.service;

import com.intendencia.gestion_morosidad_api.deuda.dto.DeudaFiltro;
import com.intendencia.gestion_morosidad_api.deuda.dto.DeudaResponse;
import com.intendencia.gestion_morosidad_api.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.deuda.mapper.DeudaMapper;
import com.intendencia.gestion_morosidad_api.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.deuda.repository.DeudaSpecifications;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DeudaService {

    private final DeudaRepository deudaRepository;
    private final Clock clock;

    public PaginaResponse<DeudaResponse> listar(DeudaFiltro filtro, Pageable pageable) {
        LocalDate hoy = LocalDate.now(clock);
        return PaginaResponse.de(
                deudaRepository.findAll(DeudaSpecifications.conFiltro(filtro), pageable)
                        .map(deuda -> DeudaMapper.toResponse(deuda, hoy)));
    }

    public DeudaResponse obtener(Long id) {
        return DeudaMapper.toResponse(buscar(id), LocalDate.now(clock));
    }

    public List<DeudaResponse> listarPorPadron(String numeroPadron) {
        LocalDate hoy = LocalDate.now(clock);
        return deudaRepository.findByPadronNumeroPadronOrderByDeudaDesdeAsc(numeroPadron.trim()).stream()
                .map(deuda -> DeudaMapper.toResponse(deuda, hoy))
                .toList();
    }

    @Transactional
    public DeudaResponse actualizarEstado(Long id, EstadoDeuda nuevoEstado) {
        Deuda deuda = buscar(id);
        EstadoDeuda anterior = deuda.getEstado();
        deuda.setEstado(nuevoEstado);
        // Sin datos personales en el log: solo id y estados
        log.info("Estado de deuda {} actualizado: {} -> {}", id, anterior, nuevoEstado);
        return DeudaMapper.toResponse(deudaRepository.save(deuda), LocalDate.now(clock));
    }

    private Deuda buscar(Long id) {
        return deudaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la deuda con id " + id));
    }
}
