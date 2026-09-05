package com.kdh.solvego.domain.ai.exception;

public class AiTimeoutException extends RuntimeException {

    public AiTimeoutException() {
        super("AI analysis timed out");
    }
}