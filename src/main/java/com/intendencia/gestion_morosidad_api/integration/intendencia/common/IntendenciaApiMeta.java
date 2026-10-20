package com.intendencia.gestion_morosidad_api.integration.intendencia.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntendenciaApiMeta {
    private Long total;

    private Object parameters;

    @JsonProperty("current_page") private Integer currentPage;
    @JsonProperty("per_page") private Integer perPage;
    @JsonProperty("last_page") private Integer lastPage;
    private Long from;
    private Long to;
    @JsonProperty("has_more") private Boolean hasMore;

    public IntendenciaApiMeta(Long total, Object parameters) {
        this.total = total;
        this.parameters = parameters;
    }
}
