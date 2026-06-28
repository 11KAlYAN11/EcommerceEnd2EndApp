package com.ecommerce.health;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents the health status of the application.
 *
 * This is a plain Java object (POJO) — no Spring annotations.
 * It is NOT a Bean. It's just a data container — it holds data
 * that will be serialized to JSON by Jackson.
 *
 * Design decision: Why a dedicated class instead of a Map<String,Object>?
 *   Maps lose type safety. Anyone can put any key.
 *   A dedicated class documents the exact shape of the response.
 *   Jackson will serialize this exactly: { "status": "UP", "service": "..." }
 */
@Getter
@Builder
public class HealthStatus {

    private String status;
    private String service;
    private String version;
    private String profile;
    private long uptimeSeconds;
}
