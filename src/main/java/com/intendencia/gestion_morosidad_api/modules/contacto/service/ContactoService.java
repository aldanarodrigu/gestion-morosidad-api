package com.intendencia.gestion_morosidad_api.modules.contacto.service;

import com.intendencia.gestion_morosidad_api.modules.contacto.dto.ContactoRequest;
import com.intendencia.gestion_morosidad_api.modules.contacto.dto.ContactoResponse;
import com.intendencia.gestion_morosidad_api.modules.contacto.entity.Contacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.repository.ContactoRepository;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;

@Service
@RequiredArgsConstructor
public class ContactoService {

    private final ContactoRepository contactoRepository;
    private final PadronRepository padronRepository;

    @Transactional(readOnly = true)
    public List<ContactoResponse> listarPorCm(String cm) {
        return listarPorCm(cm, OrigenDatos.INTENDENCIA);
    }

    @Transactional(readOnly = true)
    public List<ContactoResponse> listarPorCm(String cm, OrigenDatos origen) {

        Contribuyente contribuyente = obtenerContribuyentePorCm(cm, origen);

        return contactoRepository.findByContribuyente(contribuyente)
                .stream()
                .map(this::convertirAResponse)
                .toList();
    }

    @Transactional
    public ContactoResponse guardarOActualizar(
            String cm,
            ContactoRequest request
    ) {
        return guardarOActualizar(cm, request, OrigenDatos.INTENDENCIA);
    }

    @Transactional
    public ContactoResponse guardarOActualizar(String cm, ContactoRequest request, OrigenDatos origen) {

        Contribuyente contribuyente = obtenerContribuyentePorCm(cm, origen);

        Contacto contacto;

        if (request.id() == null) {
            contacto = new Contacto();
            contacto.setContribuyente(contribuyente);
        } else {
            contacto = contactoRepository
                    .findByIdAndContribuyente(
                            request.id(),
                            contribuyente
                    )
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Contacto no encontrado para este contribuyente"));
        }

        contacto.setTipoContacto(request.tipoContacto());
        contacto.setValor(request.valor().trim());
        contacto.setEsDeContribuyente(
                request.esDeContribuyente()
        );
        contacto.setOrigen(request.origen().trim());

        contacto = contactoRepository.save(contacto);

        return convertirAResponse(contacto);
    }

    private Contribuyente obtenerContribuyentePorCm(String cm, OrigenDatos origen) {

        Padron padron = (origen == OrigenDatos.INTENDENCIA ? padronRepository.findByCm(cm) : padronRepository.findByCmAndOrigen(cm, origen))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un padrón con el CM indicado"));

        return padron.getContribuyente();
    }

    private ContactoResponse convertirAResponse(Contacto contacto) {

        return new ContactoResponse(
                contacto.getId(),
                contacto.getTipoContacto(),
                contacto.getValor(),
                contacto.getEsDeContribuyente(),
                contacto.getOrigen()
        );
    }
}
