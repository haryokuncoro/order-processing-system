package com.haryokuncoro.ops.service;

import com.haryokuncoro.ops.dto.RegisterRequest;
import com.haryokuncoro.ops.entity.User;
import com.haryokuncoro.ops.exception.BadRequestException;
import com.haryokuncoro.ops.exception.NotFoundException;
import com.haryokuncoro.ops.exception.UnauthorizedException;
import com.haryokuncoro.ops.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service @Slf4j
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public String register(RegisterRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();
        userRepo.findByEmail(email).ifPresent(u -> {
            throw new BadRequestException("User already registered. Please Login");
        });

        if (!password.matches("^(?=.*[A-Z])(?=.*\\d).{8,}$")) {
            throw new BadRequestException("Weak password");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setEnabled(true);
        userRepo.save(user);
        return jwtService.generate(user.getId());
    }

    public String login(String email, String password) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!encoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Wrong Username or password");
        }
        return jwtService.generate(user.getId());
    }

}