package com.studyroom.config;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

/**
 * Converts application failures into consistent RFC 9457 problem responses.
 * Internal exception messages are deliberately not returned to API clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        String title = status == null ? "Request failed" : status.getReasonPhrase();
        String detail = exception.getReason() == null ? title : exception.getReason();
        return problem(exception.getStatusCode().value(), title, detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidArgument(IllegalArgumentException exception) {
        return problem(400, "Bad Request", exception.getMessage() == null
                ? "Invalid request" : exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> handleInvalidInput(Exception exception) {
        return problem(400, "Bad Request", "Request data is missing or invalid");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled API error", exception);
        return problem(500, "Internal Server Error", "An unexpected error occurred");
    }

    private static ResponseEntity<ProblemDetail> problem(int status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setTitle(title);
        body.setDetail(detail);
        body.setType(URI.create("about:blank"));
        return ResponseEntity.status(status).body(body);
    }
}
