package com.eventhive.eventhive_backend.config;

import com.eventhive.eventhive_backend.entity.Role;
import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.repository.RoleRepository;
import com.eventhive.eventhive_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// add the import
import com.eventhive.eventhive_backend.entity.Category;
import com.eventhive.eventhive_backend.repository.CategoryRepository;
import java.util.List;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DataSeeder — provisions baseline data on startup (roles + default admin).
 *
 * Interview Q: "How does your app behave on a fresh clone?"
 * Answer: It's self-bootstrapping. A CommandLineRunner seeds the three roles
 * (if missing) and the default admin (if missing), both idempotently — so
 * "clone, run" works with no manual SQL. Re-running never duplicates anything.
 *
 * Spring Boot Concept: CommandLineRunner.run() executes once, automatically,
 * right after the application context is ready.
 */
@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository; 

    public DataSeeder(UserRepository userRepository,
                      RoleRepository roleRepository,
                      CategoryRepository categoryRepository,   // NEW
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.categoryRepository = categoryRepository;          // NEW
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedRolesIfMissing();   // must run BEFORE admin seeding
        seedCategoriesIfMissing();  // NEW
        seedAdminIfMissing();
     
    }

    /**
     * Ensures the three baseline roles exist. Idempotent — only inserts
     * the ones that are missing, so it's safe on every startup.
     *
     * This is what fixes the fresh-clone crash: previously the admin seeder
     * assumed roles were inserted manually via SQL. Now the app provisions
     * them itself.
     */
    private void seedRolesIfMissing() {
        List<String> requiredRoles = List.of("ADMIN", "ORGANIZER", "ATTENDEE");

        for (String roleName : requiredRoles) {
            if (roleRepository.findByRoleName(roleName).isEmpty()) {
                Role role = Role.builder().roleName(roleName).build();
                roleRepository.save(role);
                log.info("Seeded missing role: {}", roleName);
            }
        }
    }

    /**
     * Creates the default ADMIN account if it doesn't already exist.
     * ADMIN is never self-registered via the API — only provisioned here.
     */
    private void seedAdminIfMissing() {
        String adminEmail = "admin@eventhive.com";

        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin account already exists — skipping seed.");
            return;
        }

        // Roles are guaranteed to exist now (seeded above), so this won't fail.
        Role adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN role missing after seeding — should never happen"));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        User admin = User.builder()
                .name("Platform Admin")
                .email(adminEmail)
                .password(passwordEncoder.encode("Admin@1234")) // dev only; externalize for prod
                .isActive(true)
                .accountLocked(false)
                .failedLoginAttempts(0)
                .roles(roles)
                .build();

        userRepository.save(admin);
        log.info("Default ADMIN account created: {}", adminEmail);
    }

    private void seedCategoriesIfMissing() {
        List<String> categories = List.of(
                "Music", "Technology", "Business", "Sports", "Arts", "Food");

        for (String name : categories) {
            if (categoryRepository.findByName(name).isEmpty()) {
                categoryRepository.save(Category.builder().name(name).build());
                log.info("Seeded missing category: {}", name);
            }
        }
    }
}