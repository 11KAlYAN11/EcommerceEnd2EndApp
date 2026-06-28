package com.ecommerce.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entity.
 *
 * JpaRepository<User, Long>:
 *   First type param  = Entity class (User)
 *   Second type param = Primary key type (Long)
 *
 * What JpaRepository gives us for FREE (no implementation needed):
 *   save(user)          → INSERT or UPDATE
 *   findById(id)        → SELECT WHERE id = ?   returns Optional<User>
 *   findAll()           → SELECT * FROM users
 *   delete(user)        → DELETE WHERE id = ?
 *   count()             → SELECT COUNT(*)
 *   existsById(id)      → SELECT 1 WHERE id = ?
 *   findAll(Pageable)   → SELECT ... LIMIT ? OFFSET ? (pagination)
 *
 * Custom query methods — Spring Data derives SQL from method names:
 *   findByEmail(email)  → SELECT * FROM users WHERE email = ?
 *   No SQL written. No @Query annotation. Just the method name.
 *   Spring Data parses the name and generates the JPQL at startup.
 *
 * Optional<User> return type:
 *   Forces the caller to handle the "not found" case.
 *   If we returned User (nullable), callers could forget to null-check → NPE.
 *   With Optional, the type system forces: .orElseThrow(() -> new Exception())
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
