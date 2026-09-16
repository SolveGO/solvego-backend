package com.kdh.solvego.domain.payment.exception;

/** Never retains raw provider messages, request URLs, response bodies or transport causes. */
public class PaymentGatewayException extends RuntimeException {
    public enum Reason { NOT_CONFIGURED, HTTP_ERROR, COMMUNICATION_ERROR, INVALID_RESPONSE }

    private final Reason reason;
    private final Integer httpStatus;
    private final String failureCode;

    public PaymentGatewayException(Reason reason, Integer httpStatus, String failureCode) {
        super("Payment gateway request failed: " + reason);
        this.reason = reason;
        this.httpStatus = httpStatus;
        this.failureCode = failureCode;
    }

    public Reason getReason() { return reason; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getFailureCode() { return failureCode; }
}
