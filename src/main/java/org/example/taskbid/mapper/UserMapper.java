package org.example.taskbid.mapper;

import lombok.RequiredArgsConstructor;
import org.example.taskbid.dto.RegisterRequest;
import org.example.taskbid.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class UserMapper {
    private final PasswordEncoder passwordEncoder;

    public User mapUserFromRegisterRequest(RegisterRequest request) {
        return User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .createdAt(LocalDateTime.now())
                .build();
    }
}