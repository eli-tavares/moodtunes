package com.tavares.recommender.controller;

import com.tavares.recommender.service.AiResponseException;
import com.tavares.recommender.service.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns failures into RFC 7807 JSON. Validation and malformed-request errors (400/415/...) are handled by the
 * parent class; everything else gets a generic message, with the real cause only in the server log.
 */
@RestControllerAdvice(basePackageClasses = ApiController.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Says which field was rejected and why, using only our own validation messages and never the value that was
     * sent. "Invalid request content." alone is useless when a phone is running an old copy of the app.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .limit(3)
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        if (detail.isBlank()) {
            detail = "Invalid request content.";
        }
        log.warn("Rejected {}: {}", request.getDescription(false), detail);
        return createResponseEntity(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail),
            headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        log.warn("Rejected {}: unreadable request body ({})", request.getDescription(false),
            ex.getMostSpecificCause().getClass().getSimpleName());
        return createResponseEntity(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "The request body could not be read. It must be valid JSON."), headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AiResponseException.class)
    public ProblemDetail handleAiFailure(AiResponseException ex) {
        log.warn("AI failure: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
            "We couldn't get a good answer from the AI right now. Please try again.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.");
    }
}
