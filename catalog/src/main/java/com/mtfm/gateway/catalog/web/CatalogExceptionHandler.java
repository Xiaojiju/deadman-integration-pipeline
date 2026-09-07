package com.mtfm.gateway.catalog.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 配置域与命令入口的参数错误统一成 {@code {"error":"..."}}，方便 UI toast。
 *
 * <pre>{@code
 * POST /catalog/products  body={}
 * → 400 {"error":"产品 code 不能为空"}
 * }</pre>
 */
@RestControllerAdvice(basePackages = "com.mtfm.gateway")
public class CatalogExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidBody(MethodArgumentNotValidException ex) {
        return badRequest(firstFieldMessage(ex.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> invalidConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse("参数无效");
        return badRequest(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return badRequest(ex.getMessage() == null ? "参数无效" : ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage() == null ? "状态冲突" : ex.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    private static String firstFieldMessage(FieldError error) {
        if (error == null) {
            return "参数无效";
        }
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? "参数无效" : message;
    }
}
