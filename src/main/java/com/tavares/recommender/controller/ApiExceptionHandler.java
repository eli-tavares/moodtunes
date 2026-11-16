package com.tavares.recommender.controller;

import com.tavares.recommender.service.AiResponseException;
import com.tavares.recommender.service.BadRequestException;
import com.tavares.recommender.service.ForbiddenException;
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
     *
     * @param ex      the validation failure
     * @param headers headers to send
     * @param status  the status chosen by the framework (replaced by 400)
     * @param request the failing request
     * @return a 400 problem response naming up to three fields
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

    /**
     * Answers a body that is not valid JSON, without echoing any of it back.
     *
     * @param ex      the parse failure
     * @param headers headers to send
     * @param status  the status chosen by the framework (replaced by 400)
     * @param request the failing request
     * @return a 400 problem response
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        log.warn("Rejected {}: unreadable request body ({})", request.getDescription(false),
            ex.getMostSpecificCause().getClass().getSimpleName());
        return createResponseEntity(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "The request body could not be read. It must be valid JSON."), headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Maps our own validation failures to 400.
     *
     * @param ex the failure; its message is written by us and is safe to show
     * @return a 400 problem response
     */
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Maps "not allowed" to 403.
     *
     * @param ex the failure; its message is written by us and is safe to show
     * @return a 403 problem response
     */
    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Maps a failed or unusable model answer to 502 with a friendly generic message; the real reason is only logged.
     *
     * @param ex the failure
     * @return a 502 problem response
     */
    @ExceptionHandler(AiResponseException.class)
    public ProblemDetail handleAiFailure(AiResponseException ex) {
        log.warn("AI failure: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
            "We couldn't get a good answer from the AI right now. Please try again.");
    }

    /**
     * Catches everything else and answers with a generic 500, so no internal detail (a stack trace, a password in a
     * connection string) can leak. The real cause goes to the server log.
     *
     * @param ex the unexpected failure
     * @return a 500 problem response
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.");
    }
}
