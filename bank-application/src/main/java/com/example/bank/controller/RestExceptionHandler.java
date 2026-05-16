package com.example.bank.controller;

import com.example.bank.dto.ErrorResponse;
import com.example.bank.exception.BankException;
import com.example.bank.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(BankException.class)
    public ResponseEntity<ErrorResponse> handleBankException(BankException ex, HttpServletRequest request) {
        HttpStatus status = statusFor(ex.getErrorCode());
        ErrorResponse body = ErrorResponse.of(ex.getErrorCode().name(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        ErrorResponse body = ErrorResponse.validation("Request validation failed", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        ErrorResponse body = ErrorResponse.of("MALFORMED_JSON", "Request body is malformed", request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    private HttpStatus statusFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case ACCOUNT_NOT_FOUND, TRANSFER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_ACCOUNT_DATA, INVALID_AMOUNT, INVALID_BLOCK_REASON -> HttpStatus.BAD_REQUEST;
            case ACCOUNT_BLOCKED,
                    ACCOUNT_ALREADY_BLOCKED,
                    INSUFFICIENT_FUNDS,
                    SAME_ACCOUNT_TRANSFER,
                    CURRENCY_MISMATCH -> HttpStatus.CONFLICT;
        };
    }
}
