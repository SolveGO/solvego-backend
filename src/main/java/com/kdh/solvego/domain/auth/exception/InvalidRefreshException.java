package com.kdh.solvego.domain.auth.exception;

public class InvalidRefreshException extends RuntimeException {
    public InvalidRefreshException() { super("Invalid or expired refresh session"); }
}
