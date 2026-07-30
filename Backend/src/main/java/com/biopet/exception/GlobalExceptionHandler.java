package com.biopet.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailDuplicadoException.class)
    public ResponseEntity<ProblemDetail> emailDuplicado(EmailDuplicadoException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.CONFLICT, ProblemType.CONFLICT, "Conflicto de datos", ex.getMessage(), request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.NOT_FOUND, ProblemType.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> credencialesInvalidas(BadCredentialsException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.UNAUTHORIZED, ProblemType.UNAUTHORIZED, "No autenticado", "Credenciales inválidas", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, List<String>> errores = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errores.computeIfAbsent(fieldError.getField(), key -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }

        ProblemDetail problemDetail = ProblemDetailFactory.build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemType.VALIDATION,
                "Error de validación",
                "Uno o más campos contienen valores inválidos.",
                request
        );
        problemDetail.setProperty("errors", errores);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> argumentoInvalido(IllegalArgumentException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.BAD_REQUEST, ProblemType.BAD_REQUEST, "Solicitud inválida", ex.getMessage(), request);
    }

    private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, ProblemType type, String title,
                                                            String detail, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetailFactory.build(status, type, title, detail, request);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
