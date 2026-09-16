package com.kdh.solvego.domain.payment.gateway.toss;

import com.kdh.solvego.domain.payment.gateway.PaymentGateway;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;

class TossPaymentConfigTest {
    @Test
    void applicationYamlWiresGatewayWithoutASecret() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
                .withUserConfiguration(TossPaymentConfig.class, TossPaymentGateway.class)
                .withPropertyValues("payment.toss.secret-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PaymentGateway.class);
                    assertThat(context).hasBean("tossRestClient");
                });
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 59})
    void rejectsReadTimeoutBelowTossMinimum(long seconds) {
        assertThatThrownBy(() -> new TossPaymentConfig().tossRestClient(RestClient.builder(),
                "https://api.tosspayments.com", Duration.ofSeconds(5), Duration.ofSeconds(seconds)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnlimitedConnectTimeout() {
        assertThatThrownBy(() -> new TossPaymentConfig().tossRestClient(RestClient.builder(),
                "https://api.tosspayments.com", Duration.ZERO, Duration.ofSeconds(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
