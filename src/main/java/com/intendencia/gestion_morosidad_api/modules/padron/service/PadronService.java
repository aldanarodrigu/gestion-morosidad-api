package com.intendencia.gestion_morosidad_api.modules.padron.service;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PadronService {

    private final PadronRepository padronRepository;

    /**
     * El número de padrón se repite entre localidades, así que devuelve todos los padrones con ese
     * número. Con {@code localidad} se queda solo con los de esa localidad (sin distinguir mayúsculas).
     */
    @Transactional
    public List<PadronResponse> buscarPorNumeroPadron(String numeroPadron, String localidad) {
        return buscarPadrones(numeroPadron, localidad).stream()
                .map(PadronResponse::de)
                .toList();
    }

    @Transactional
    public List<ContribuyenteResponse> buscarContribuyentes(String numeroPadron, String localidad) {
        return buscarPadrones(numeroPadron, localidad).stream()
                .map(ContribuyenteResponse::de)
                .toList();
    }

    private List<Padron> buscarPadrones(String numeroPadron, String localidad) {
        // Solo lee la base: los padrones se cargan en la sincronización, no durante una consulta
        List<Padron> padrones = padronRepository.findByNumeroPadronOrderByLocalidadAscCmAsc(numeroPadron);
        if (localidad == null || localidad.isBlank()) {
            return padrones;
        }
        String buscada = localidad.trim();
        return padrones.stream()
                .filter(padron -> padron.getLocalidad() != null && padron.getLocalidad().trim().equalsIgnoreCase(buscada))
                .toList();
    }

}
