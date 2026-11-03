package org.example.survey.api;

import org.example.survey.database.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(basePackages = "org.example.survey.api")
public class ApiAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiAdvice.class);

    private static ResponseEntity<Map<String, Object>> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status, "error", message));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        return error(status, status == 404 ? "Не найдено" : status == 403 ? "Доступ запрещён" : "Ошибка запроса");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
        return error(400, "Некорректное тело запроса");
    }

    @ExceptionHandler(DatabaseException.class)
    public ResponseEntity<Map<String, Object>> handleDatabase(DatabaseException e) {
        log.error("Database error", e);
        return error(500, "Ошибка при работе с базой данных");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("Unexpected API error", e);
        return error(500, "Внутренняя ошибка сервера");
    }
}
