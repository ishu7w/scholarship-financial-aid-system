package com.scholarai.aid.domain;

/** Expected business failure that is safe to show to the applicant. */
public class AidException extends RuntimeException {
    private final int status;
    public AidException(String message) { this(400, message); }
    public AidException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
