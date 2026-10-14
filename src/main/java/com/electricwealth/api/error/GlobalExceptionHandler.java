package com.electricwealth.api.error;

import java.util.stream.Collectors;

import com.electricwealth.api.web.TraceIds;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tools.jackson.databind.DatabindException;

/**
 * Turns exceptions into the error envelope, with the same status codes and error codes as the Node API.
 * Validation messages list each failing field as {@code field: message}, separated by {@code ; }.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorEnvelope> api(ApiException e, HttpServletRequest req) {
        return envelope(req, e.status(), e.code(), e.getMessage());
    }

    /** A {@code @Valid @RequestBody} failed Bean Validation. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorEnvelope> invalidBody(MethodArgumentNotValidException e, HttpServletRequest req) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return validation(req, detail.isEmpty() ? "(root): invalid" : detail);
    }

    /** A validated {@code @RequestParam} or {@code @PathVariable} failed. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorEnvelope> invalidParams(HandlerMethodValidationException e, HttpServletRequest req) {
        String detail = e.getParameterValidationResults().stream()
                .flatMap(r -> r instanceof ParameterErrors errors
                        // A @Valid @RequestBody reported through method validation: name the fields.
                        ? errors.getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage())
                        : r.getResolvableErrors().stream()
                                .map(err -> r.getMethodParameter().getParameterName() + ": " + err.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return validation(req, detail);
    }

    /** Missing or malformed JSON body, or a value of the wrong type (e.g. an unknown enum value). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorEnvelope> unreadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        if (e.getMostSpecificCause() instanceof DatabindException de && !de.getPath().isEmpty()) {
            String field = de.getPath().stream()
                    .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : String.valueOf(ref.getIndex()))
                    .collect(Collectors.joining("."));
            return validation(req, field + ": invalid value");
        }
        return validation(req, "(root): the request body could not be read as JSON");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorEnvelope> notJson(HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
        return validation(req, "(root): the request body must be JSON (Content-Type: application/json)");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorEnvelope> typeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return validation(req, e.getName() + ": invalid value");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorEnvelope> missingParam(MissingServletRequestParameterException e, HttpServletRequest req) {
        return validation(req, e.getParameterName() + ": required");
    }

    /** Unknown path, or a known path with the wrong method: both are a 404 in the Node API. */
    @ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class })
    ResponseEntity<ErrorEnvelope> noRoute(Exception e, HttpServletRequest req) {
        return envelope(req, HttpStatus.NOT_FOUND, "NOT_FOUND",
                "No route for " + req.getMethod() + " " + req.getRequestURI() + ".");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorEnvelope> unexpected(Exception e, HttpServletRequest req) {
        log.error("[trace={}] Unhandled error", TraceIds.get(req), e);
        return envelope(req, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong.");
    }

    private ResponseEntity<ErrorEnvelope> validation(HttpServletRequest req, String detail) {
        ApiException e = ApiException.validation(detail);
        return envelope(req, e.status(), e.code(), e.getMessage());
    }

    private ResponseEntity<ErrorEnvelope> envelope(HttpServletRequest req, HttpStatus status, String code,
            String message) {
        return ResponseEntity.status(status).body(ErrorEnvelope.of(code, message, TraceIds.get(req)));
    }
}
