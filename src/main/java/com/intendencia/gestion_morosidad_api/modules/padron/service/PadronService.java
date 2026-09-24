package com.intendencia.gestion_morosidad_api.modules.padron.service;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PadronService {

    private final PadronRepository padronRepository;
    private final ContribuyenteService contribuyenteService;

    @Transactional
    public Optional<PadronResponse> buscarPorNumeroPadron(String numeroPadron) {

        Optional<Padron> padron =
                padronRepository.findByNumeroPadron(numeroPadron);

        if (padron.isEmpty()) {
            contribuyenteService.sincronizarDesdeGeoPagos();
            padron = padronRepository.findByNumeroPadron(numeroPadron);
        }

        return padron.map(PadronResponse::de);
    }

    @Transactional
    public Optional<ContribuyenteResponse> buscarContribuyente(
            String numeroPadron) {

        Optional<Padron> padron =
                padronRepository.findByNumeroPadron(numeroPadron);

        if (padron.isEmpty()) {
            contribuyenteService.sincronizarDesdeGeoPagos();
            padron = padronRepository.findByNumeroPadron(numeroPadron);
        }

        return padron.map(ContribuyenteResponse::de);
    }

}
