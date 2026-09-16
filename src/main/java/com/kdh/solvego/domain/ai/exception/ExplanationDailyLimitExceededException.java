package com.kdh.solvego.domain.ai.exception;

public class ExplanationDailyLimitExceededException extends RuntimeException {
    public ExplanationDailyLimitExceededException() {
        super("Daily AI explanation limit exceeded");
    }
}
