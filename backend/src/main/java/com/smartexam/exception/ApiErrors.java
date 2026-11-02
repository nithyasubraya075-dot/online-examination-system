package com.smartexam.exception;

import com.smartexam.dto.Api.Message;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Message> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new Message(ex.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Message> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).distinct().collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new Message(message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Message> forbidden() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Message("You do not have permission to perform this action.")); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Message> unexpected(Exception ex) {
        log.error("Unhandled API error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Message("The request could not be completed."));
    }
}
