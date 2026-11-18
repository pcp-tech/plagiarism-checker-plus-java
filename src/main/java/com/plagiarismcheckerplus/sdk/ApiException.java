package com.plagiarismcheckerplus.sdk;

import java.util.Collections;
import java.util.Map;

/**
 * A non-2xx response. {@link #getErrorCode()} is the API's code, such as insufficient_words.
 * {@link #getDetails()} is the response's error object (wordsNeeded, billingUrl and other detail fields).
 */
public class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * No longer returned: free accounts get a free API trial and free test keys.
     *
     * @deprecated kept so existing code still compiles.
     */
    @Deprecated
    public static final String PLAN_REQUIRED = "plan_required";
    public static final String TEXT_TOO_SHORT = "text_too_short";
    public static final String DOC_TOO_LONG = "doc_too_long";
    /** 402: not enough words across all sources (wordsNeeded, wordsAvailable, balances, billingUrl). */
    public static final String INSUFFICIENT_WORDS = "insufficient_words";
    /** 402: the free API trial cannot cover the request (wordsNeeded, trialRemaining, trialTotal, billingUrl). */
    public static final String TRIAL_EXHAUSTED = "trial_exhausted";
    /** 402: the key's monthly word cap would be exceeded (wordsNeeded, monthlyWordCap, usedThisMonth). */
    public static final String KEY_CAP_REACHED = "key_cap_reached";
    public static final String INVALID_REQUEST = "invalid_request";
    public static final String INVALID_JSON = "invalid_json";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String INVALID_API_KEY = "invalid_api_key";
    public static final String KEY_REVOKED = "key_revoked";
    public static final String EMAIL_UNVERIFIED = "email_unverified";
    public static final String FORBIDDEN = "forbidden";
    public static final String NOT_FOUND = "not_found";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String WORKERS_UNAVAILABLE = "workers_unavailable";
    public static final String INTERNAL_ERROR = "internal_error";

    private final int status;
    private final String errorCode;
    private final Double retryAfterSeconds;
    private final transient Object body;

    public ApiException(String message, int status, String errorCode, Double retryAfterSeconds, Object body) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.body = body;
    }

    public int getStatus() { return status; }

    public String getErrorCode() { return errorCode; }

    /** Seconds from Retry-After, or null if not sent. */
    public Double getRetryAfterSeconds() { return retryAfterSeconds; }

    public Object getBody() { return body; }

    /** The response's error object: code, message and any detail fields. Empty if the body had none. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDetails() {
        if (body instanceof Map) {
            Object error = ((Map<String, Object>) body).get("error");
            if (error instanceof Map) return Collections.unmodifiableMap((Map<String, Object>) error);
        }
        return Collections.emptyMap();
    }
}
