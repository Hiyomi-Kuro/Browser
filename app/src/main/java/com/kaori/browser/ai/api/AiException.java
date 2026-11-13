package com.kaori.browser.ai.api;

/** Classified AI/network failure so callers and UI can react per cause. */
public class AiException extends Exception {

    public enum Kind {
        NOT_CONFIGURED,
        NO_API_KEY,
        AUTHENTICATION,
        FORBIDDEN,
        RATE_LIMIT,
        BAD_REQUEST,
        NOT_FOUND,
        SERVER,
        TIMEOUT,
        NETWORK,
        PARSE,
        EMPTY_RESPONSE,
        CANCELLED,
        UNKNOWN
    }

    private final Kind kind;
    private final int httpStatus;
    private final String detail;
    private final String finishReason;

    public AiException(Kind kind, int httpStatus, String message) {
        this(kind, httpStatus, message, "", null);
    }

    public AiException(Kind kind, int httpStatus, String message, Throwable cause) {
        this(kind, httpStatus, message, "", cause);
    }

    public AiException(Kind kind, int httpStatus, String message, String finishReason) {
        this(kind, httpStatus, message, finishReason, null);
    }

    public AiException(Kind kind, int httpStatus, String message, String finishReason,
                       Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.detail = message;
        this.finishReason = finishReason == null ? "" : finishReason;
    }

    public Kind getKind() {
        return kind;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDetail() {
        return detail == null ? "" : detail;
    }

    /** Provider {@code finish_reason}; empty when unknown. */
    public String getFinishReason() {
        return finishReason;
    }

    /** True when the provider stopped because it ran out of output budget. */
    public boolean isLengthTruncated() {
        return "length".equalsIgnoreCase(finishReason)
                || "max_tokens".equalsIgnoreCase(finishReason);
    }

    /** Whether retrying the same request may succeed without user intervention. */
    public boolean isRetryable() {
        switch (kind) {
            case TIMEOUT:
            case NETWORK:
            case SERVER:
            case RATE_LIMIT:
            case EMPTY_RESPONSE:
                return true;
            default:
                return false;
        }
    }

    public boolean isAuthFailure() {
        return kind == Kind.AUTHENTICATION || kind == Kind.FORBIDDEN || kind == Kind.NO_API_KEY
                || kind == Kind.NOT_CONFIGURED;
    }

    public static Kind fromHttpStatus(int status) {
        if (status == 400 || status == 422) {
            return Kind.BAD_REQUEST;
        }
        if (status == 401) {
            return Kind.AUTHENTICATION;
        }
        if (status == 403) {
            return Kind.FORBIDDEN;
        }
        if (status == 404) {
            return Kind.NOT_FOUND;
        }
        if (status == 408 || status == 504) {
            return Kind.TIMEOUT;
        }
        if (status == 429) {
            return Kind.RATE_LIMIT;
        }
        if (status >= 500) {
            return Kind.SERVER;
        }
        return Kind.UNKNOWN;
    }
}
