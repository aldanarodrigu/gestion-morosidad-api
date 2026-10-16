package com.intendencia.gestion_morosidad_api.shared.enums;

/** Origen funcional mostrado en la cartera; cada proveedor tiene sus propios identificadores. */
public enum OrigenDatos {
    INTENDENCIA, SUCIVE;

    public String proveedor() {
        return this == SUCIVE ? "GeoPat" : "GeoPagos";
    }
}
