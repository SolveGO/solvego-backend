package com.kdh.solvego.domain.user.exception;

public class CurrentPasswordMismatchException extends RuntimeException {
    public CurrentPasswordMismatchException() {
        super("Current password does not match");
    }
}
