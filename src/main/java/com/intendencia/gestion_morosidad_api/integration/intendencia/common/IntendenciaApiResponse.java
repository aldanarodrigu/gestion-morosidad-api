package com.intendencia.gestion_morosidad_api.integration.intendencia.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntendenciaApiResponse<T> {
    private List<T> data;

    private IntendenciaApiMeta meta;
}
