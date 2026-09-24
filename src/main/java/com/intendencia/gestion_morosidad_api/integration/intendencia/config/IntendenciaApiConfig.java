package com.intendencia.gestion_morosidad_api.integration.intendencia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Configuration
public class IntendenciaApiConfig {

    @Bean
    public RestClient intendenciaRestClient(
            RestClient.Builder builder,
            @Value("${geopagos.base-url}") String baseUrl,
            @Value("${geopagos.username}") String username,
            @Value("${geopagos.password}") String password,
            @Value("${geopagos.connect-timeout:10s}") Duration connectTimeout,
            @Value("${geopagos.read-timeout:180s}") Duration readTimeout) {

        // Sin timeouts, una consulta colgada deja trabada la sincronización indefinidamente
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(username, password);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }
}