package com.tavares.recommender.service;

/** The client sent something invalid. The message is written by us and is safe to show to the user. */
public class BadRequestException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what was wrong with the request; written by us, so safe to show the person
     */
    public BadRequestException(String message) {
        super(message);
    }
}
