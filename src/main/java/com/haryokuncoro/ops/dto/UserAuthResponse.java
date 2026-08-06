package com.haryokuncoro.ops.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data @Builder
public class UserAuthResponse {
    private UUID id;
    private String username;
    private String email;
}
