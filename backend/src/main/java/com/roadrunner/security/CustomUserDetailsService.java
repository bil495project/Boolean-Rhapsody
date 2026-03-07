package com.roadrunner.security;

import java.util.Collections;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.UserRepository;

@Service
@SuppressWarnings("null")

public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with id: " + userId));
        
        // bu satırı değiştirmeyin, com.roadrunner.user.entity.User ile çakışma oluyor import edince
        return new org.springframework.security.core.userdetails.User(
                user.getId(),
                user.getPasswordHash(),
                Collections.emptyList());
    }
}
