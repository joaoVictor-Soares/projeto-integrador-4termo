package com.pi.demo.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** RestClient apontando para a API Python (ia-modelo/main.py). */
@Configuration
public class IaClientConfig {

    @Bean
    public RestClient iaRestClient(IaProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.timeoutConexao())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.timeoutLeitura());

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
