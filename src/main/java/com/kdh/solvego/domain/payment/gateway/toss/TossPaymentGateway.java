package com.kdh.solvego.domain.payment.gateway.toss;

import com.kdh.solvego.domain.payment.exception.PaymentGatewayException;
import com.kdh.solvego.domain.payment.gateway.PaymentGateway;
import com.kdh.solvego.domain.payment.gateway.dto.BillingKeyResult;
import com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import static com.kdh.solvego.domain.payment.exception.PaymentGatewayException.Reason.*;

@Component
public class TossPaymentGateway implements PaymentGateway {
    private final RestClient restClient;
    private final String secretKey;

    public TossPaymentGateway(@Qualifier("tossRestClient") RestClient restClient,
                              @Value("${payment.toss.secret-key}") String secretKey) {
        this.restClient = restClient;
        this.secretKey = secretKey;
    }

    @Override
    public BillingKeyResult issueBillingKey(String authKey, String customerKey) {
        requireText(authKey, "authKey");
        requireText(customerKey, "customerKey");
        TossDtos.BillingResponse response = post("/v1/billing/authorizations/issue",
                new TossDtos.BillingRequest(authKey, customerKey), TossDtos.BillingResponse.class);
        if (response == null || !StringUtils.hasText(response.billingKey())
                || !customerKey.equals(response.customerKey())) {
            throw invalidResponse();
        }
        return new BillingKeyResult(response.billingKey(), response.customerKey());
    }

    @Override
    public PaymentApprovalResult charge(String billingKey, String customerKey,
                                        String orderId, String orderName, long amount) {
        requireText(billingKey, "billingKey");
        requireText(customerKey, "customerKey");
        requireText(orderId, "orderId");
        requireText(orderName, "orderName");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        TossDtos.ChargeResponse response = post("/v1/billing/{billingKey}",
                new TossDtos.ChargeRequest(customerKey, orderId, orderName, amount),
                TossDtos.ChargeResponse.class, billingKey);
        if (response == null || !StringUtils.hasText(response.paymentKey())
                || !orderId.equals(response.orderId()) || response.totalAmount() == null
                || response.totalAmount() != amount || !"DONE".equals(response.status())
                || response.approvedAt() == null) {
            throw invalidResponse();
        }
        return new PaymentApprovalResult(response.paymentKey(), response.orderId(),
                response.totalAmount(), response.approvedAt().toInstant());
    }

    private <T> T post(String path, Object request, Class<T> responseType, Object... variables) {
        if (!StringUtils.hasText(secretKey)) {
            throw new PaymentGatewayException(NOT_CONFIGURED, null, null);
        }
        try {
            return restClient.post().uri(path, variables)
                    .headers(headers -> headers.setBasicAuth(secretKey, ""))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request).retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (req, res) -> {
                        // RestClient's default error handler retains bodies/URLs; sanitize below.
                        throw new RestClientResponseException("Toss HTTP error", res.getStatusCode(),
                                "", res.getHeaders(), res.getBody().readAllBytes(), null);
                    })
                    .body(responseType);
        } catch (RestClientResponseException e) {
            throw new PaymentGatewayException(HTTP_ERROR, e.getStatusCode().value(), failureCode(e));
        } catch (ResourceAccessException e) {
            throw new PaymentGatewayException(COMMUNICATION_ERROR, null, null);
        } catch (RestClientException e) {
            throw invalidResponse();
        }
    }

    private String failureCode(RestClientResponseException exception) {
        // Only a bounded symbolic code is exposed; arbitrary provider text may contain secrets.
        try {
            TossDtos.ErrorResponse error = ERROR_MAPPER.readValue(
                    exception.getResponseBodyAsByteArray(), TossDtos.ErrorResponse.class);
            return error != null && error.code() != null && error.code().matches("[A-Z][A-Z0-9_]{0,99}")
                    ? error.code() : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper ERROR_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static PaymentGatewayException invalidResponse() {
        return new PaymentGatewayException(INVALID_RESPONSE, null, null);
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
