package com.intendencia.gestion_morosidad_api.modules.contribuyente.service;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContribuyenteService {

    private final GeoPagosClient geoPagosClient;
    private final ContribuyenteRepository contribuyenteRepository;
    private final PadronRepository padronRepository;

    @Transactional
    public List<ContribuyenteResponse> listarContribuyentes() {

        sincronizarDesdeGeoPagos();

        return contribuyenteRepository.findAll()
                .stream()
                .map(this::convertirAResponse)
                .toList();
    }

    @Transactional
    public Optional<ContribuyenteResponse> buscarPorCm(String cm) {

        Optional<Contribuyente> contribuyente =
                contribuyenteRepository.findByCm(cm);

        if (contribuyente.isEmpty()) {
            sincronizarDesdeGeoPagos();
            contribuyente = contribuyenteRepository.findByCm(cm);
        }

        return contribuyente.map(this::convertirAResponse);
    }

    @Transactional
    public void sincronizarDesdeGeoPagos() {

        var respuesta = geoPagosClient.obtenerContribuyentes();

        if (respuesta == null || respuesta.getData() == null) {
            return;
        }

        respuesta.getData().forEach(this::guardarOActualizar);
    }

    private void guardarOActualizar(GeoPagosContribuyenteDto dto) {

        String cm = convertirAString(dto.cm());

        if (cm == null) {
            return;
        }

        // Contribuyente
        Contribuyente contribuyente = contribuyenteRepository
                .findByCm(cm)
                .orElseGet(Contribuyente::new);

        contribuyente.setCm(cm);
        contribuyente.setNombre(dto.nombrePersonas());

        contribuyente = contribuyenteRepository.save(contribuyente);

        // Padrón
        Padron padron = padronRepository
                .findByCm(cm)
                .orElseGet(Padron::new);

        padron.setCm(cm);
        padron.setNumeroPadron(convertirAString(dto.numeroPadron()));
        padron.setTipoPadron(dto.tipoPadron());
        padron.setLocalidad(dto.localidad());
        padron.setBlock(convertirAString(dto.block()));
        padron.setUnidad(convertirAString(dto.unidad()));
        padron.setContribuyente(contribuyente);

        padronRepository.save(padron);
    }

    private ContribuyenteResponse convertirAResponse(
            Contribuyente contribuyente) {

        return new ContribuyenteResponse(
                contribuyente.getCm(),
                contribuyente.getNombre(),
                contribuyente.getDocumento()
        );
    }

    private String convertirAString(Object valor) {
        return valor != null ? valor.toString() : null;
    }
}