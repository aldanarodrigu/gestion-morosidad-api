package com.intendencia.gestion_morosidad_api.integration.intendencia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
public class IntendenciaApiConfig {

    @Bean
    public RestClient intendenciaRestClient(
            RestClient.Builder builder,
            @Value("${geopagos.base-url}") String baseUrl,
            @Value("${geopagos.username}") String username,
            @Value("${geopagos.password}") String password) {

        return builder
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(username, password);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }
}