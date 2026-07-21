package com.kdh.solvego.domain.problem.exception;

public class ProblemAccessDeniedException extends RuntimeException {

    public ProblemAccessDeniedException() {
        super("You do not have permission to modify this problem");
    }
}