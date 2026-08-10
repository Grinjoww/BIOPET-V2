package com.biopet.exception;

import java.net.URI;

public enum ProblemType {
    VALIDATION("urn:biopet:error:validation"),
    NOT_FOUND("urn:biopet:error:not-found"),
    CONFLICT("urn:biopet:error:conflict"),
    UNAUTHORIZED("urn:biopet:error:unauthorized"),
    FORBIDDEN("urn:biopet:error:forbidden"),
    BAD_REQUEST("urn:biopet:error:bad-request"),
    RATE_LIMITED("urn:biopet:error:rate-limited"),
    BAD_GATEWAY("urn:biopet:error:external-service"),
    INTERNAL("urn:biopet:error:internal");

    private final URI uri;

    ProblemType(String urn) {
        this.uri = URI.create(urn);
    }

    public URI uri() {
        return uri;
    }
}