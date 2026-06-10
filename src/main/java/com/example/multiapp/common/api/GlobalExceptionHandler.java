package com.example.multiapp.common.api;

import com.example.multiapp.common.web.IdempotencyConflictException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ApiError.FieldViolation toViolation(FieldError fe) {
        String msg = fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage();
        return new ApiError.FieldViolation(fe.getField(), msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(MethodArgumentNotValidException ex) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toViolation)
                .toList();
        return ApiError.of("BAD_REQUEST", "Validation failed", violations);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError illegalArg(IllegalArgumentException ex) {
        return ApiError.of("BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError optimistic(Exception ex) {
        return ApiError.of("CONFLICT", "Optimistic lock conflict");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError dataIntegrity(DataIntegrityViolationException ex) {
        return ApiError.of("CONFLICT", "Data integrity conflict");
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError conflict(ConflictException ex) {
        return ApiError.of("CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError idempotencyConflict(IdempotencyConflictException ex) {
        return ApiError.of("IDEMPOTENCY_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError forbidden(ForbiddenException ex) {
        return ApiError.of("FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(NotFoundException ex) {
        return ApiError.of("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError accessDenied(AccessDeniedException ex) {
        return ApiError.of("FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError authentication(AuthenticationException ex) {
        return ApiError.of("UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(PreconditionFailedException.class)
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    public ApiError preconditionFailed(PreconditionFailedException ex) {
        return ApiError.of("PRECONDITION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError unknown(Exception ex) {
//        System.out.println(">>>>>>>");
//        System.out.println(ex);
//        ex.printStackTrace();
        return ApiError.of("INTERNAL_ERROR", "Unexpected error");
    }
}
