package com.ecommerce.address;

import com.ecommerce.common.audit.Auditable;
import com.ecommerce.user.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a shipping/billing address belonging to a user.
 *
 * Why not embed address fields directly on the User?
 *   Users can have MULTIPLE addresses (home, office, parent's house).
 *   A user picks which address to ship to when placing an order.
 *   Separate table with user_id FK cleanly handles multiple addresses.
 *
 * is_default:
 *   One address can be marked as default. When placing an order,
 *   the default address is pre-selected.
 *   Business rule (Phase 6): when user sets a new default, the old
 *   default must be unset. This is a transactional operation.
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "street", nullable = false, length = 255)
    private String street;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "pincode", nullable = false, length = 10)
    private String pincode;

    @Column(name = "country", nullable = false, length = 100)
    @Builder.Default
    private String country = "India";

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
