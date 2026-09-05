package com.kdh.solvego.global.exception;

import com.kdh.solvego.domain.auth.exception.InvalidLoginException;
import com.kdh.solvego.domain.problem.exception.ProblemOwnershipException;
import com.kdh.solvego.domain.problem.exception.ProblemNotFoundException;
import com.kdh.solvego.domain.user.exception.DuplicateUsernameException;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import com.kdh.solvego.domain.ai.exception.AiServerException;
import com.kdh.solvego.domain.ai.exception.AiTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateUsernameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateUsernameException(
            DuplicateUsernameException e
    ) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(InvalidLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidLoginException(
            InvalidLoginException e
    ) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e
    ) {
        return new ErrorResponse("Invalid request");
    }

    @ExceptionHandler(ProblemNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProblemNotFoundException(
            ProblemNotFoundException e
    ){
        return new ErrorResponse(e.getMessage());
    }


    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFoundException(
            UserNotFoundException e
    ) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(ProblemOwnershipException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleProblemOwnershipException(
            ProblemOwnershipException e
    ) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(AiServerException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleAiServerException(
            AiServerException e
    ) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(AiTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public ErrorResponse handleAiTimeoutException(
            AiTimeoutException e
    ) {
        return new ErrorResponse(e.getMessage());
    }
}
