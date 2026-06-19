package com.eventhive.eventhive_backend.security;

import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

   
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Force-initialize the LAZY roles collection while the session is open.
        // This triggers the SELECT on user_roles NOW, so CustomUserDetails
        // can safely read roles later (in the filter, after session closes).
        user.getRoles().size();

        return new CustomUserDetails(user);
    }
}