package com.ecommerce.config;

/** Same pattern as order-service -- payment-service also forwards the caller's own JWT to order-service. */
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
