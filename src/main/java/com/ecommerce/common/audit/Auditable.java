package com.ecommerce.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base class for all entities that need audit timestamps.
 *
 * @MappedSuperclass:
 *   This class is NOT its own entity — it has no table.
 *   Its fields are INHERITED and added to the child entity's table.
 *   User extends Auditable → users table gets created_at and updated_at columns.
 *   Product extends Auditable → products table gets them too.
 *   One place to maintain, used everywhere.
 *
 * @EntityListeners(AuditingEntityListener.class):
 *   Registers a JPA listener that watches for INSERT and UPDATE events.
 *   On INSERT → sets @CreatedDate field automatically
 *   On UPDATE → sets @LastModifiedDate field automatically
 *   We never set these manually — Hibernate handles it.
 *
 * @CreatedDate / @LastModifiedDate:
 *   Spring Data JPA audit annotations.
 *   Requires @EnableJpaAuditing on the main application class (we add that next).
 *
 * updatable = false on created_at:
 *   The creation timestamp should NEVER change after the row is inserted.
 *   This constraint is enforced at the JPA level — even if someone tries
 *   to set it, Hibernate won't include it in UPDATE statements.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class Auditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
