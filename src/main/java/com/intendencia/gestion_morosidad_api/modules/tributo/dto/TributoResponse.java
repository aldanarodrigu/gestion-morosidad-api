package com.intendencia.gestion_morosidad_api.modules.tributo.dto;

import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import java.util.Comparator;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;

public record TributoResponse(Long id, String codigo, String descripcion, OrigenDatos origen) {

    public TributoResponse(Long id, String codigo, String descripcion) {
        this(id, codigo, descripcion, OrigenDatos.INTENDENCIA);
    }

    /** Orden numérico de los códigos ("801" antes que "3802"), aunque se guarden como texto. */
    public static final Comparator<Tributo> POR_CODIGO = Comparator
            .comparingInt((Tributo t) -> t.getCodigo().length())
            .thenComparing(Tributo::getCodigo);

    public static TributoResponse de(Tributo tributo) {
        return new TributoResponse(tributo.getId(), tributo.getCodigo(), tributo.getDescripcion(), tributo.getOrigen());
    }
}
