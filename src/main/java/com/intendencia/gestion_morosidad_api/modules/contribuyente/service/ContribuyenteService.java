package com.intendencia.gestion_morosidad_api.modules.contribuyente.service;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronSpecifications;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.util.Optional;

/**
 * Consultas de contribuyentes. Solo lee la base: los datos se cargan desde GeoPagos o GeoPat en la
 * sincronización (POST /api/sincronizaciones/deudas), nunca durante una consulta.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContribuyenteService {

    private final PadronRepository padronRepository;

    /** Contribuyentes con deuda vigente (un registro por padrón), filtrables por nombre y documento. */
    public PaginaResponse<ContribuyenteResponse> listarContribuyentes(String nombre, String documento, Pageable pageable) {
        return PaginaResponse.de(
                padronRepository.findAll(PadronSpecifications.conDeudaVigente(nombre, documento), pageable)
                        .map(ContribuyenteResponse::de));
    }

    public Optional<ContribuyenteResponse> buscarPorCm(String cm) {
        return padronRepository.findByCm(cm).map(ContribuyenteResponse::de);
    }

    public Optional<ContribuyenteResponse> buscarPorCm(String cm, OrigenDatos origen) {
        return padronRepository.findByCmAndOrigen(cm, origen).map(ContribuyenteResponse::de);
    }

    public Optional<List<PadronResponse>> listarPadronesPorCm(String cm, OrigenDatos origen) {
        return padronRepository.findByCmAndOrigen(cm, origen).map(p -> padronRepository
                .findByContribuyenteOrderByNumeroPadronAsc(p.getContribuyente()).stream().map(PadronResponse::de).toList());
    }

    /** Todos los padrones del contribuyente vinculado al CM. */
    public Optional<List<PadronResponse>> listarPadronesPorCm(String cm) {
        return padronRepository.findByCm(cm)
                .map(padron -> padronRepository
                        .findByContribuyenteOrderByNumeroPadronAsc(padron.getContribuyente())
                        .stream()
                        .map(PadronResponse::de)
                        .toList());
    }
}
