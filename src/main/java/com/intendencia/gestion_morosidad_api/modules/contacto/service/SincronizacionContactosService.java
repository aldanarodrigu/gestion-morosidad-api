package com.intendencia.gestion_morosidad_api.modules.contacto.service;

import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.entity.Contacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.repository.ContactoRepository;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Actualiza solo los contactos importados de cada fuente; los contactos manuales quedan intactos. */
@Service
@RequiredArgsConstructor
public class SincronizacionContactosService {

    public static final String PERSONAS = "API_PERSONAS";
    public static final String GEOPAGOS = "API_GEOPAGOS";
    public static final String PENDIENTES = "API_FACTURAS_PENDIENTES";

    private final ContactoRepository contactoRepository;

    public record Dato(Contribuyente contribuyente, TipoContacto tipo, String origen,
                       String valor, boolean esDeContribuyente) {
    }

    @Transactional
    public void sincronizar(List<Dato> datos) {
        if (datos.isEmpty()) {
            return;
        }

        Map<Clave, Dato> ultimos = new LinkedHashMap<>();
        for (Dato dato : datos) {
            ultimos.put(new Clave(dato.contribuyente().getId(), dato.tipo(), dato.origen()), dato);
        }
        List<Contribuyente> contribuyentes = datos.stream().map(Dato::contribuyente).distinct().toList();
        Map<Clave, Contacto> existentes = new HashMap<>();
        List<Contacto> borrar = new ArrayList<>();
        for (Contacto contacto : contactoRepository.findByContribuyenteIn(contribuyentes)) {
            Clave clave = new Clave(contacto.getContribuyente().getId(),
                    contacto.getTipoContacto(), contacto.getOrigen());
            if (ultimos.containsKey(clave) && existentes.putIfAbsent(clave, contacto) != null) {
                borrar.add(contacto);
            }
        }

        List<Contacto> guardar = new ArrayList<>();
        for (Dato dato : ultimos.values()) {
            Clave clave = new Clave(dato.contribuyente().getId(), dato.tipo(), dato.origen());
            Contacto actual = existentes.get(clave);
            String valor = dato.valor() == null ? null : dato.valor().trim();
            if (valor != null && valor.length() > 255) {
                valor = valor.substring(0, 255);
            }
            if (valor == null || valor.isEmpty()) {
                if (actual != null) {
                    borrar.add(actual);
                    existentes.remove(clave);
                }
            } else if (actual == null) {
                Contacto nuevo = new Contacto();
                nuevo.setContribuyente(dato.contribuyente());
                nuevo.setTipoContacto(dato.tipo());
                nuevo.setOrigen(dato.origen());
                nuevo.setValor(valor);
                nuevo.setEsDeContribuyente(dato.esDeContribuyente());
                guardar.add(nuevo);
                existentes.put(clave, nuevo);
            } else if (!Objects.equals(actual.getValor(), valor)
                    || !Objects.equals(actual.getEsDeContribuyente(), dato.esDeContribuyente())) {
                actual.setValor(valor);
                actual.setEsDeContribuyente(dato.esDeContribuyente());
                guardar.add(actual);
            }
        }
        contactoRepository.deleteAll(borrar);
        contactoRepository.saveAll(guardar);
    }

    private record Clave(Long contribuyenteId, TipoContacto tipo, String origen) {
    }
}
