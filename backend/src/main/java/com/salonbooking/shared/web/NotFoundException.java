package com.salonbooking.shared.web;

/**
 * Something does not exist for the current business. Also used for records of other businesses,
 * so callers cannot tell the two apart.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
