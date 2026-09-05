package com.kdh.solvego.domain.ai.exception;

public class AiServerException extends RuntimeException {

    public AiServerException() {
        super("Failed to communicate with AI server");
    }
}