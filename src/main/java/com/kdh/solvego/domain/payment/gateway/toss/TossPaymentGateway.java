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
        TossDtos.BillingRequest request =
                new TossDtos.BillingRequest(authKey, customerKey);

        TossDtos.BillingResponse response = post(
                "/v1/billing/authorizations/issue",
                request,
                TossDtos.BillingResponse.class
        );

        validateBillingResponse(response, customerKey);

        return new BillingKeyResult(
                response.billingKey(),
                response.customerKey()
        );
    }

    @Override
    public PaymentApprovalResult charge(
            String billingKey,
            String customerKey,
            String orderId,
            String orderName,
            long amount
    ) {
        validateChargeRequest(billingKey, customerKey, orderId, orderName, amount);

        TossDtos.ChargeRequest request =
                new TossDtos.ChargeRequest(customerKey, orderId, orderName, amount);

        TossDtos.ChargeResponse response = post(
                "/v1/billing/{billingKey}",
                request,
                TossDtos.ChargeResponse.class,
                billingKey
        );

        validateChargeResponse(response, orderId, amount);

        return toPaymentApprovalResult(response);
    }

    private <T> T post(
            String path,
            Object request,
            Class<T> responseType,
            Object... variables
    ) {
        validateSecretKey();

        try {
            return executePost(path, request, responseType, variables);
        } catch (RestClientResponseException e) {
            throw toHttpError(e);
        } catch (ResourceAccessException e) {
            throw communicationError();
        } catch (RestClientException e) {
            throw invalidResponse();
        }
    }

    private static void validateBillingResponse(
            TossDtos.BillingResponse response,
            String customerKey
    ) {
        if (response == null
                || !StringUtils.hasText(response.billingKey())
                || !customerKey.equals(response.customerKey())) {
            throw invalidResponse();
        }
    }

    private static void validateChargeRequest(
            String billingKey,
            String customerKey,
            String orderId,
            String orderName,
            long amount
    ) {
        requireText(billingKey, "billingKey");
        requireText(customerKey, "customerKey");
        requireText(orderId, "orderId");
        requireText(orderName, "orderName");

        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private static void validateChargeResponse(
            TossDtos.ChargeResponse response,
            String orderId,
            long amount
    ) {
        if (response == null
                || !StringUtils.hasText(response.paymentKey())
                || !orderId.equals(response.orderId())
                || response.totalAmount() == null
                || response.totalAmount() != amount
                || !"DONE".equals(response.status())
                || response.approvedAt() == null) {
            throw invalidResponse();
        }
    }

    private static PaymentApprovalResult toPaymentApprovalResult(
            TossDtos.ChargeResponse response
    ) {
        return new PaymentApprovalResult(
                response.paymentKey(),
                response.orderId(),
                response.totalAmount(),
                response.approvedAt().toInstant()
        );
    }

    private <T> T executePost(
            String path,
            Object request,
            Class<T> responseType,
            Object... variables
    ) {
        return restClient.post()
                .uri(path, variables)
                .headers(headers -> headers.setBasicAuth(secretKey, ""))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        (req, res) -> {
                            throw new RestClientResponseException(
                                    "Toss HTTP error",
                                    res.getStatusCode(),
                                    "",
                                    res.getHeaders(),
                                    res.getBody().readAllBytes(),
                                    null
                            );
                        }
                )
                .body(responseType);
    }

    private void validateSecretKey() {
        if (!StringUtils.hasText(secretKey)) {
            throw new PaymentGatewayException(NOT_CONFIGURED, null, null);
        }
    }

    private PaymentGatewayException toHttpError(RestClientResponseException e) {
        return new PaymentGatewayException(
                HTTP_ERROR,
                e.getStatusCode().value(),
                failureCode(e)
        );
    }

    private PaymentGatewayException communicationError() {
        return new PaymentGatewayException(
                COMMUNICATION_ERROR,
                null,
                null
        );
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
