package com.haryokuncoro.ops.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMerchantRequest {
    @NotNull
    private String merchantCode;
    @NotNull
    private String merchantName;
    private String stripeAccountId;
    private String email;
    private String phone;
}
