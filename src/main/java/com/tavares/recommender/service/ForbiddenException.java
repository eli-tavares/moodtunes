package com.tavares.recommender.service;

/** The caller is known but is not allowed to do this. The message is ours and safe to show. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
