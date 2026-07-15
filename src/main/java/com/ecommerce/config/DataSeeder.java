package com.ecommerce.config;

import com.ecommerce.category.Category;
import com.ecommerce.category.CategoryRepository;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.user.Role;
import com.ecommerce.user.Role.RoleName;
import com.ecommerce.user.RoleRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Seeds demo data on first startup (when DB is empty).
 * Safe to run repeatedly — all inserts are guarded by existence checks.
 *
 * Demo accounts:
 *   admin@test.com  / Admin@123   → ROLE_ADMIN
 *   user@test.com   / User@123    → ROLE_USER
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        Role adminRole = ensureRole(RoleName.ROLE_ADMIN);
        Role userRole  = ensureRole(RoleName.ROLE_USER);

        seedUsers(adminRole, userRole);

        if (categoryRepository.count() == 0) {
            seedCategoriesAndProducts();
        }

        log.info("=== DataSeeder complete — DB is ready ===");
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    private Role ensureRole(RoleName name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(new Role(name)));
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private void seedUsers(Role adminRole, Role userRole) {
        if (!userRepository.existsByEmail("admin@test.com")) {
            userRepository.save(User.builder()
                    .firstName("Admin")
                    .lastName("ShopEase")
                    .email("admin@test.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .phone("9999999999")
                    .roles(Set.of(adminRole))
                    .build());
            log.info("Seeded admin user: admin@test.com / Admin@123");
        }

        if (!userRepository.existsByEmail("user@test.com")) {
            userRepository.save(User.builder()
                    .firstName("Demo")
                    .lastName("User")
                    .email("user@test.com")
                    .password(passwordEncoder.encode("User@123"))
                    .phone("8888888888")
                    .roles(Set.of(userRole))
                    .build());
            log.info("Seeded demo user: user@test.com / User@123");
        }
    }

    // ── Categories + Products ─────────────────────────────────────────────────

    private void seedCategoriesAndProducts() {
        Category electronics = save(Category.builder()
                .name("Electronics")
                .description("Phones, laptops, gadgets and accessories")
                .imageUrl("https://images.unsplash.com/photo-1498049794561-7780e7231661?w=400")
                .build());

        Category clothing = save(Category.builder()
                .name("Clothing")
                .description("Men's, women's and kids fashion")
                .imageUrl("https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=400")
                .build());

        Category books = save(Category.builder()
                .name("Books")
                .description("Programming, fiction, self-help and more")
                .imageUrl("https://images.unsplash.com/photo-1512820790803-83ca734da794?w=400")
                .build());

        Category homeKitchen = save(Category.builder()
                .name("Home & Kitchen")
                .description("Appliances, cookware and home décor")
                .imageUrl("https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=400")
                .build());

        Category sports = save(Category.builder()
                .name("Sports")
                .description("Fitness, outdoor and sports equipment")
                .imageUrl("https://images.unsplash.com/photo-1517649763962-0c623066013b?w=400")
                .build());

        // Sub-categories
        Category phones = save(Category.builder()
                .name("Smartphones")
                .description("Android and iOS smartphones")
                .parent(electronics)
                .build());

        Category laptops = save(Category.builder()
                .name("Laptops")
                .description("Gaming, ultrabook and workstation laptops")
                .parent(electronics)
                .build());

        // Electronics products
        seedProducts(List.of(
            Product.builder()
                .name("iPhone 15 Pro")
                .description("Apple iPhone 15 Pro with A17 Pro chip, 48MP camera, titanium design. 256GB storage.")
                .price(new BigDecimal("134900.00"))
                .stockQuantity(50)
                .imageUrl("https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=400")
                .category(phones)
                .build(),

            Product.builder()
                .name("Samsung Galaxy S24 Ultra")
                .description("Samsung Galaxy S24 Ultra with Snapdragon 8 Gen 3, 200MP camera, built-in S Pen.")
                .price(new BigDecimal("124999.00"))
                .stockQuantity(35)
                .imageUrl("https://images.unsplash.com/photo-1706016637552-b3ba57fb92d7?w=400")
                .category(phones)
                .build(),

            Product.builder()
                .name("MacBook Air M3")
                .description("Apple MacBook Air with M3 chip, 8GB RAM, 256GB SSD, 15-inch Liquid Retina display.")
                .price(new BigDecimal("114900.00"))
                .stockQuantity(20)
                .imageUrl("https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=400")
                .category(laptops)
                .build(),

            Product.builder()
                .name("Sony WH-1000XM5 Headphones")
                .description("Industry-leading noise cancellation, 30-hour battery, multipoint connection.")
                .price(new BigDecimal("29990.00"))
                .stockQuantity(60)
                .imageUrl("https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400")
                .category(electronics)
                .build(),

            Product.builder()
                .name("iPad Pro 12.9\"")
                .description("Apple iPad Pro with M2 chip, Liquid Retina XDR display, Thunderbolt port. 256GB WiFi.")
                .price(new BigDecimal("99900.00"))
                .stockQuantity(25)
                .imageUrl("https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=400")
                .category(electronics)
                .build()
        ));

        // Clothing products
        seedProducts(List.of(
            Product.builder()
                .name("Men's Casual Hoodie")
                .description("Premium cotton-blend hoodie, relaxed fit, kangaroo pocket. Available in multiple colors.")
                .price(new BigDecimal("1499.00"))
                .stockQuantity(100)
                .imageUrl("https://images.unsplash.com/photo-1556821840-3a63f15732ce?w=400")
                .category(clothing)
                .build(),

            Product.builder()
                .name("Women's Running Shoes")
                .description("Lightweight mesh upper, responsive cushioning, breathable design. Perfect for daily runs.")
                .price(new BigDecimal("3999.00"))
                .stockQuantity(80)
                .imageUrl("https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=400")
                .category(clothing)
                .build(),

            Product.builder()
                .name("Classic Denim Jacket")
                .description("Timeless denim jacket with button closure, chest pockets. Unisex fit.")
                .price(new BigDecimal("2499.00"))
                .stockQuantity(45)
                .imageUrl("https://images.unsplash.com/photo-1548126032-079a0fb0099d?w=400")
                .category(clothing)
                .build()
        ));

        // Books
        seedProducts(List.of(
            Product.builder()
                .name("Clean Code — Robert C. Martin")
                .description("A handbook of agile software craftsmanship. Essential reading for every developer.")
                .price(new BigDecimal("699.00"))
                .stockQuantity(200)
                .imageUrl("https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400")
                .category(books)
                .build(),

            Product.builder()
                .name("Designing Data-Intensive Applications")
                .description("By Martin Kleppmann. The bible of modern backend systems and distributed databases.")
                .price(new BigDecimal("2499.00"))
                .stockQuantity(150)
                .imageUrl("https://images.unsplash.com/photo-1532012197267-da84d127e765?w=400")
                .category(books)
                .build(),

            Product.builder()
                .name("Atomic Habits — James Clear")
                .description("Tiny changes, remarkable results. The #1 New York Times bestseller on habit building.")
                .price(new BigDecimal("499.00"))
                .stockQuantity(300)
                .imageUrl("https://images.unsplash.com/photo-1589829085413-56de8ae18c73?w=400")
                .category(books)
                .build()
        ));

        // Home & Kitchen
        seedProducts(List.of(
            Product.builder()
                .name("Instant Pot Duo 7-in-1")
                .description("Pressure cooker, slow cooker, rice cooker, steamer, sauté pan, yogurt maker and warmer.")
                .price(new BigDecimal("7999.00"))
                .stockQuantity(40)
                .imageUrl("https://images.unsplash.com/photo-1585515320310-259814833e62?w=400")
                .category(homeKitchen)
                .build(),

            Product.builder()
                .name("Dyson V15 Detect Vacuum")
                .description("Laser reveals hidden dust. HEPA filtration captures 99.97% of particles. 60-min runtime.")
                .price(new BigDecimal("49900.00"))
                .stockQuantity(15)
                .imageUrl("https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=400")
                .category(homeKitchen)
                .build()
        ));

        // Sports
        seedProducts(List.of(
            Product.builder()
                .name("Yoga Mat — Non-Slip 6mm")
                .description("Eco-friendly TPE yoga mat with alignment lines, carry strap. 183x61cm, 6mm thickness.")
                .price(new BigDecimal("899.00"))
                .stockQuantity(120)
                .imageUrl("https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=400")
                .category(sports)
                .build(),

            Product.builder()
                .name("Adjustable Dumbbell Set (5–52.5 lbs)")
                .description("Replaces 15 sets of weights. Select weight with a twist. Compact, space-saving design.")
                .price(new BigDecimal("24999.00"))
                .stockQuantity(30)
                .imageUrl("https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=400")
                .category(sports)
                .build()
        ));

        log.info("Seeded 5 categories and 15 demo products");
    }

    private Category save(Category c) {
        return categoryRepository.save(c);
    }

    private void seedProducts(List<Product> products) {
        productRepository.saveAll(products);
    }
}
