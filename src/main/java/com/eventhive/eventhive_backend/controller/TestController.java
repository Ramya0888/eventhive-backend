package com.eventhive.eventhive_backend.controller;

import com.eventhive.eventhive_backend.dto.ApiResponse;
import com.eventhive.eventhive_backend.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TestController — temporary endpoints to verify JWT security + RBAC.
 * (We'll delete this once real modules are built — it's just to prove
 * the security chain works end to end.)
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    /**
     * Any authenticated user can hit this (needs a valid JWT).
     * Proves: JwtAuthenticationFilter validates the token and sets SecurityContext.
     *
     * @AuthenticationPrincipal injects the currently logged-in user —
     * Interview Q: "How do you get the current user in a controller?"
     */
    @GetMapping("/me")
    public ApiResponse<String> whoAmI(@AuthenticationPrincipal CustomUserDetails userDetails) {
        String info = "Logged in as: " + userDetails.getUsername()
                + " | Roles: " + userDetails.getAuthorities();
        return ApiResponse.success("Authenticated", info);
    }

    /**
     * Only ADMIN can access — proves @PreAuthorize role check works.
     * Interview Q: "How do you restrict an endpoint to a specific role?"
     */
    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> adminOnly() {
        return ApiResponse.success("Access granted", "You are an ADMIN");
    }

    /**
     * Only ORGANIZER can access.
     */
    @GetMapping("/organizer-only")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ApiResponse<String> organizerOnly() {
        return ApiResponse.success("Access granted", "You are an ORGANIZER");
    }
}