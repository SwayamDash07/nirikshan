package com.nirikshan.config;
import com.nirikshan.service.ResourceNotFoundException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestControllerAdvice public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class) ResponseEntity<Map<String,String>> notFound(ResourceNotFoundException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage())); }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,String>> badRequest(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
}
