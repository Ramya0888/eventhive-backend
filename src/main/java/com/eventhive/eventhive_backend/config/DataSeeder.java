package com.eventhive.eventhive_backend.config;

import com.eventhive.eventhive_backend.entity.Role;
import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.repository.RoleRepository;
import com.eventhive.eventhive_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * DataSeeder — creates the default ADMIN account on startup if none exists.
 *
 * Interview Q: "How is the admin account created if users can't self-register as admin?"
 * Answer: A CommandLineRunner seeds one admin at startup. ADMIN accounts are
 * never created through the public API — only seeded/provisioned internally.
 *
 * Spring Boot Concept: CommandLineRunner — its run() method executes once,
 * automatically, right after the application starts.
 */
@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      RoleRepository roleRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String adminEmail = "admin@eventhive.com";

        // Only seed if admin doesn't already exist (idempotent — safe to run every startup)
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin account already exists — skipping seed.");
            return;
        }

        Role adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseThrow(() -> new RuntimeException("ADMIN role not found in DB"));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        User admin = User.builder()
                .name("Platform Admin")
                .email(adminEmail)
                .password(passwordEncoder.encode("Admin@1234")) // change after first login
                .isActive(true)
                .accountLocked(false)
                .failedLoginAttempts(0)
                .roles(roles)
                .build();

        userRepository.save(admin);
        log.info(" Default ADMIN account created: {} (password: Admin@1234)", adminEmail);
    }
}