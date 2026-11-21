package com.EOT.project.exception;

/**
 * A client sent an edit made earlier than the last change already saved for the resource.
 * Carries the current state so the client can show what won.
 */
public class StaleWriteException extends RuntimeException {
    private final transient Object current;

    public StaleWriteException(String message, Object current) {
        super(message);
        this.current = current;
    }

    public Object getCurrent() {
        return current;
    }
}
