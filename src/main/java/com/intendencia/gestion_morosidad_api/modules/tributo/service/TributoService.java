package com.intendencia.gestion_morosidad_api.modules.tributo.service;

import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TributoService {

    private final TributoRepository tributoRepository;

    public List<TributoResponse> listar() {
        return tributoRepository.findAllOrdenados().stream()
                .map(TributoResponse::de)
                .toList();
    }
}
