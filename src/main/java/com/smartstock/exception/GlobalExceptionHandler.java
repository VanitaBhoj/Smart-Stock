package com.smartstock.exception;

import com.smartstock.dto.ApiError;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(
            DuplicateResourceException ex,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.CONFLICT, "BUSINESS_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ApiError> handleInsufficientInventory(
            InsufficientInventoryException ex,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.CONFLICT, "BUSINESS_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(
            InsufficientStockException ex,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.CONFLICT, "BUSINESS_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidStateTransition(
            InvalidStateTransitionException ex,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.CONFLICT, "BUSINESS_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler({ReservationStateException.class, OrderStateException.class})
    public ResponseEntity<ApiError> handleInvalidBusinessState(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.CONFLICT, "BUSINESS_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    public ResponseEntity<ApiError> handleOptimisticLock(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.CONFLICT,
                "BUSINESS_ERROR",
                "The resource changed during this request. Reload it and try again.",
                request
        );
    }

    @ExceptionHandler(InventoryReservationLockBusyException.class)
    public ResponseEntity<ApiError> handleInventoryLockBusy(
            InventoryReservationLockBusyException ex,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.CONFLICT, "BUSINESS_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(InventoryReservationLockUnavailableException.class)
    public ResponseEntity<ApiError> handleInventoryLockUnavailable(
            InventoryReservationLockUnavailableException ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICE_UNAVAILABLE",
                "Inventory reservation coordination is temporarily unavailable. Retry shortly.",
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return buildError(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(
            Exception ex,
            HttpServletRequest request
    ) {
        String message;
        if (ex instanceof IllegalArgumentException && ex.getMessage() != null) {
            message = ex.getMessage();
        } else if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "Parameter '" + mismatch.getName() + "' has an invalid value.";
        } else if (ex instanceof MissingServletRequestParameterException missing) {
            message = "Required parameter '" + missing.getParameterName() + "' is missing.";
        } else {
            message = "Request body is invalid or malformed.";
        }
        return buildError(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.CONFLICT,
                "BUSINESS_ERROR",
                "The request conflicts with existing data.",
                request
        );
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleRouteNotFound(
            Exception ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The requested API resource was not found.",
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.METHOD_NOT_ALLOWED,
                "BUSINESS_ERROR",
                "The HTTP method is not supported for this resource.",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled API error for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred.",
                request
        );
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request
    ) {
        return buildError(status, error, message, request, null);
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }
}
