package com.ecommerce.user;

import com.ecommerce.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a registered user of the platform.
 *
 * Extends Auditable → inherits created_at and updated_at columns automatically.
 *
 * @Table(uniqueConstraints):
 *   We define a UNIQUE constraint on email at the table level.
 *   This is equivalent to @Column(unique=true) but is the preferred approach
 *   when you want to name the constraint explicitly (helpful in error messages
 *   and migrations).
 *
 * WHY HashSet for roles (not List)?
 *   Roles are a Set because:
 *   1. A user should never have the same role twice
 *   2. Sets use equals/hashCode for uniqueness — correct semantics
 *   3. Hibernate has a known issue with @ManyToMany + List where
 *      it deletes ALL rows and re-inserts on any change (very inefficient)
 *      Sets avoid this problem.
 *
 * @ManyToMany(fetch = FetchType.EAGER) for roles:
 *   This is one of the FEW cases where EAGER is acceptable.
 *   Roles are small (3-5 records), always needed for auth checks,
 *   and loaded with the user on every security context lookup.
 *   Using LAZY here would cause LazyInitializationException in security filters.
 *
 * @JoinTable:
 *   Creates the user_roles join table with:
 *     user_id FK → references users.id
 *     role_id FK → references roles.id
 *   Without @JoinTable, Hibernate would name the table "user_roles" anyway,
 *   but explicit naming = explicit control.
 *
 * enabled field:
 *   Soft-enables/disables a user without deleting them.
 *   A banned user has enabled=false. Spring Security checks this field.
 *   This is better than hard-deleting: audit trail preserved.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
