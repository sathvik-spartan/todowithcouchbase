package com.example.todowithcouchbase.Task.exception;

import java.io.Serial;

public class TaskWithThisNameAlreadyExistException extends RuntimeException{

    @Serial
    private static final long serialVersionUID = -3495060754159297663L;

    private static final String DEFAULT_MESSAGE = """
            Task with this name already exist
            """;

    public TaskWithThisNameAlreadyExistException() {
        super(DEFAULT_MESSAGE);
    }

    public TaskWithThisNameAlreadyExistException(final String message) {
        super(DEFAULT_MESSAGE + " " + message);
    }
}
