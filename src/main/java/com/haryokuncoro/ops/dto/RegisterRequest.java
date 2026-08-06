package com.haryokuncoro.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter @Builder
public class RegisterRequest {
    @NotBlank
    @Schema(description = "username", defaultValue = "Admin")
    private String username;

    @NotBlank
    @Email
    @Schema(description = "user email", defaultValue = "admin@mail.com")
    private String email;

    @NotBlank
    @Schema(description = "user password", defaultValue = "Admin123!")
    private String password;
}