package com.nirikshan.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(com.nirikshan.service.ResourceNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(com.nirikshan.service.ResourceNotFoundException e, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(com.nirikshan.service.CvProcessingDisabledException.class)
    ResponseEntity<Map<String, Object>> cvDisabled(com.nirikshan.service.CvProcessingDisabledException e, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST, message.isBlank() ? "Request validation failed" : message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        String message = e.getCause() instanceof InvalidFormatException
                ? "Request contains a value with an invalid format"
                : "Request body is missing or invalid JSON";
        return response(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Database constraint failure for {} {}", request.getMethod(), request.getRequestURI(), e);
        return response(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> database(DataAccessException e, HttpServletRequest request) {
        log.error("Database failure for {} {}", request.getMethod(), request.getRequestURI(), e);
        return response(HttpStatus.SERVICE_UNAVAILABLE, "Database is unavailable", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled API failure for {} {}", request.getMethod(), request.getRequestURI(), e);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message, HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        body.put("message", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        body.put("status", status.value());
        body.put("path", request.getRequestURI());
        body.put("requestId", requestId == null ? "unknown" : requestId);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
