package com.kdh.solvego.domain.payment.gateway.toss;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TossPaymentConfig {
    @Bean
    public RestClient tossRestClient(
            RestClient.Builder builder,
            @Value("${payment.toss.base-url}") String baseUrl,
            @Value("${payment.toss.connect-timeout}") Duration connectTimeout,
            @Value("${payment.toss.read-timeout}") Duration readTimeout
    ) {
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout.compareTo(Duration.ofSeconds(60)) < 0) {
            throw new IllegalArgumentException("Toss requires a positive connect timeout and read timeout >= 60s");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(readTimeout);
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
