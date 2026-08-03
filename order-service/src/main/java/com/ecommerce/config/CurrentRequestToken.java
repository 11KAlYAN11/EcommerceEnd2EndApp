package com.ecommerce.config;

/**
 * Holds the raw incoming "Bearer ..." token for the duration of one request
 * (ThreadLocal, same lifetime idea as MDC's correlation ID -- ADR B-12).
 *
 * WHY THIS EXISTS: order-service's own filter already validated this token
 * to authenticate the request. But CartClient/ProductClient/AddressClient
 * then need to call OTHER services on this same user's behalf, and those
 * services need to see and validate that same token themselves (each one
 * independently checks the signature -- nobody just trusts order-service).
 * So the raw token has to be threaded through, not just its decoded claims.
 */
public class CurrentRequestToken {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    public static void set(String rawToken) {
        TOKEN.set(rawToken);
    }

    public static String get() {
        return TOKEN.get();
    }

    public static void clear() {
        TOKEN.remove();
    }
}
