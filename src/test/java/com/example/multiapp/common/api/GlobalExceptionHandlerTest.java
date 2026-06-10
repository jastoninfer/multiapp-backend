package com.example.multiapp.common.api;

import com.example.multiapp.common.web.IdempotencyConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void idempotencyConflict_returns409ApiError() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ApiError error = handler.idempotencyConflict(
                new IdempotencyConflictException("Idempotency-Key reused with different request body"));

        assertThat(error.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(error.message()).isEqualTo("Idempotency-Key reused with different request body");

        Method method = GlobalExceptionHandler.class
                .getMethod("idempotencyConflict", IdempotencyConflictException.class);
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);

        assertThat(status).isNotNull();
        assertThat(status.code()).isEqualTo(HttpStatus.CONFLICT);
    }
}
