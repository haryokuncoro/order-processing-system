package com.haryokuncoro.ops.controller;

import com.haryokuncoro.ops.dto.ApiResponse;
import com.haryokuncoro.ops.dto.LoginRequest;
import com.haryokuncoro.ops.dto.RegisterRequest;
import com.haryokuncoro.ops.dto.UserAuthResponse;
import com.haryokuncoro.ops.entity.User;
import com.haryokuncoro.ops.service.AuthService;
import com.haryokuncoro.ops.util.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        String token = authService.register(request);
        Map<String, Object> resp = Map.of("token", token);
        return ResponseUtil.success(resp);
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest req, HttpServletRequest servletRequest) {
        String token = authService.login(req.getEmail(), req.getPassword());
        Map<String, Object> resp = Map.of("token", token);
        return ResponseUtil.success(resp);
    }

    @GetMapping("/me")
    public ApiResponse<UserAuthResponse> me(@AuthenticationPrincipal User user) {
        return ResponseUtil.success(
                UserAuthResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .build()
        );
    }
}