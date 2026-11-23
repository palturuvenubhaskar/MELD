package com.meld.tool;

/**
 * A tool call that failed in a way that is safe to describe to the caller.
 * Messages never include request headers, credentials or response bodies.
 */
public class ToolExecutionException extends RuntimeException {

    private final String code;

    public ToolExecutionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ToolExecutionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
