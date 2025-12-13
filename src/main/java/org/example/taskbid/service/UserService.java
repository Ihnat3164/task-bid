package org.example.taskbid.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.taskbid.component.JwtUtil;
import org.example.taskbid.dto.LoginRequest;
import org.example.taskbid.dto.LoginResponse;
import org.example.taskbid.dto.RegisterRequest;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.User;
import org.example.taskbid.exception.BusinessException;
import org.example.taskbid.exception.NotFoundException;
import org.example.taskbid.mapper.UserMapper;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class UserService {

    JwtUtil jwtUtil;
    UserRepository userRepository;
    ProfileRepository profileRepository;
    PasswordEncoder passwordEncoder;
    UserMapper userMapper;

    public LoginResponse registerUser(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("USER_ALREADY_EXISTS", "User with this email already exists");
        }
        userRepository.save(userMapper.mapUserFromRegisterRequest(request));

        return LoginResponse.builder()
                .token(jwtUtil.generateToken("CUSTOMER" ,request.getEmail()))
                .build();
    }

    public LoginResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(request.getEmail());
        if (user.isEmpty()) {
            throw new NotFoundException("USER_NOT_EXISTS", "User doesn't exist");
        }
        if (isPasswordValid(user, request.getPassword())) {
            Profile profile = profileRepository.findByUser(userRepository.findByEmail(request.getEmail()).get()).get();
            return LoginResponse.builder()
                    .token(jwtUtil.generateToken(profile.getRole().name(), request.getEmail()))
                    .build();
        }
        throw new RuntimeException("Password is incorrect");
    }

    private boolean isPasswordValid(Optional<User> userOpt, String password ) {
        return userOpt
            .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .isPresent();
    }
}
