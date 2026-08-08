package com.haryokuncoro.ops.dto;

import com.haryokuncoro.ops.dto.enums.MerchantStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data @Builder
public class GetMerchantResponse {
    private UUID id;
    private String merchantCode;
    private String merchantName;
    private String stripeAccountId;
    private String email;
    private String phone;
    private MerchantStatus status;
}
