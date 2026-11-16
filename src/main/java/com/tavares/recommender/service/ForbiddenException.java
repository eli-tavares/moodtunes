package com.tavares.recommender.service;

/** The caller is known but is not allowed to do this. The message is ours and safe to show. */
public class ForbiddenException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message why the action is not allowed; written by us, so safe to show the person
     */
    public ForbiddenException(String message) {
        super(message);
    }
}
