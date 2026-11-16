package com.tavares.recommender.service;

/** The upstream AI service failed or returned something we cannot use. */
public class AiResponseException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what went wrong, for the server log (never shown to the person)
     */
    public AiResponseException(String message) {
        super(message);
    }

    /**
     * Creates the exception with the underlying cause.
     *
     * @param message what went wrong, for the server log (never shown to the person)
     * @param cause   the original failure
     */
    public AiResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
