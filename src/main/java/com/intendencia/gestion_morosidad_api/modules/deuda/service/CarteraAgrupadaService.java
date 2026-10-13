package com.intendencia.gestion_morosidad_api.modules.deuda.service;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.*;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@RequiredArgsConstructor
// Las consultas de grupos y deudas ven la misma foto aunque se sincronice en paralelo.
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CarteraAgrupadaService {
    private final CarteraAgrupadaRepository agrupadaRepository;
    private final DeudaRepository deudaRepository;
    private final DeudaService deudaService;

    public PaginaResponse<DeudasAgrupadasResponse> listar(DeudaFiltro filtro, Pageable pageable) {
        var pagina = agrupadaRepository.buscarGrupos(filtro, pageable);
        if (pagina.isEmpty()) return new PaginaResponse<>(List.of(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
        var especificacion = DeudaSpecifications.conFiltro(filtro).and(
                (root, query, cb) -> CarteraAgrupadaRepository.clave(root, cb).in(pagina.getContent()));
        var deudas = deudaRepository.findAll(especificacion,
                Pageable.unpaged(Sort.by(Sort.Order.asc("deudaDesde"), Sort.Order.asc("id")))).getContent();
        var grupos = deudaService.mapearDeudas(deudas).stream()
                .collect(Collectors.groupingBy(CarteraAgrupadaService::clave));
        return PaginaResponse.de(pagina.map(k -> resumir(k, grupos.get(k))));
    }

    private static String clave(DeudaResponse deuda) {
        String doc = deuda.contribuyente().documento();
        // Igual que CriteriaBuilder.trim: solo elimina espacios exteriores, conserva el identificador.
        String documento = doc == null ? "" : doc.replaceAll("^ +| +$", "");
        return documento.isEmpty() ? "ID:" + deuda.contribuyenteId() : "DOC:" + documento;
    }

    private static DeudasAgrupadasResponse resumir(String clave, List<DeudaResponse> deudas) {
        var segmento = deudas.stream().map(DeudaResponse::segmentoMora).filter(Objects::nonNull)
                .max(Comparator.comparing(SegmentoMoraResponse::orden)).orElse(null);
        return new DeudasAgrupadasResponse(clave,
                clave.startsWith("DOC:") ? clave.substring(4) : null,
                deudas.stream().map(d -> d.contribuyente().nombre()).filter(Objects::nonNull).distinct().sorted().toList(),
                deudas.stream().map(DeudaResponse::importe).reduce(BigDecimal.ZERO, BigDecimal::add),
                deudas.stream().map(DeudaResponse::diasAtraso).filter(Objects::nonNull).max(Long::compareTo).orElse(null),
                segmento, deudas);
    }
}
