package com.ecommerce.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper used by EVERY endpoint in this application.
 *
 * Why we need this:
 *   Without a standard wrapper, every endpoint returns a different shape.
 *   Frontend devs don't know if they'll get a String, an object, or an array.
 *   With this wrapper, they always read response.data — predictable, consistent.
 *
 * @JsonInclude(NON_NULL):
 *   Fields with null value are EXCLUDED from JSON output.
 *   If 'data' is null, it won't appear in the JSON at all — cleaner response.
 *
 * @Builder (Lombok):
 *   Instead of:  new ApiResponse(true, "OK", data, timestamp)
 *   We write:    ApiResponse.builder().success(true).message("OK").data(data).build()
 *   Builder pattern prevents argument order mistakes and makes code self-documenting.
 *
 * @Getter (Lombok):
 *   Generates getters for all fields. Jackson uses getters to serialize to JSON.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Static factory methods — convenience for common cases

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
