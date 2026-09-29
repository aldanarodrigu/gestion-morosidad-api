package com.intendencia.gestion_morosidad_api.modules.contacto.dto;

import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;

public record ContactoResponse(

        Long id,

        TipoContacto tipoContacto,

        String valor,

        Boolean esDeContribuyente,

        String origen

) {
}