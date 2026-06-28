package com.ecommerce.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a security role (ROLE_USER, ROLE_ADMIN).
 *
 * Why a separate table for roles?
 *   Storing roles as a string column on users (e.g., role="ADMIN")
 *   breaks when a user can have multiple roles.
 *   A separate roles table + a join table (user_roles) handles this cleanly.
 *
 * @Enumerated(EnumType.STRING):
 *   Stores the enum as its String name ("ROLE_USER") in the DB.
 *   NOT as an integer ordinal (0, 1) — ordinals break if enum order changes.
 *   Always use EnumType.STRING for safety.
 *
 * RoleName enum:
 *   Spring Security expects roles to be prefixed with "ROLE_".
 *   When we check hasRole("USER"), Spring looks for "ROLE_USER" internally.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private RoleName name;

    public Role(RoleName name) {
        this.name = name;
    }

    public enum RoleName {
        ROLE_USER,
        ROLE_ADMIN,
        ROLE_SELLER
    }
}
