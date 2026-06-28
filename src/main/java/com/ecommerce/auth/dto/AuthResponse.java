package com.ecommerce.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class AuthResponse {
    private String token;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
}
