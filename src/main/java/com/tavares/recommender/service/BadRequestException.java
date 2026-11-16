package com.tavares.recommender.service;

/** The client sent something invalid. The message is written by us and is safe to show to the user. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
