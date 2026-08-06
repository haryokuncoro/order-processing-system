package com.haryokuncoro.ops.controller;


import com.haryokuncoro.ops.dto.ApiResponse;
import com.haryokuncoro.ops.dto.CreateMerchantRequest;
import com.haryokuncoro.ops.dto.UpdateMerchantRequest;
import com.haryokuncoro.ops.entity.Merchant;
import com.haryokuncoro.ops.service.MerchantService;
import com.haryokuncoro.ops.util.ResponseUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/merchants")
@Slf4j
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public ApiResponse getAllMerchants(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String stripeAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Merchant> resp = merchantService.getAllMerchants(name, email, stripeAccountId, pageable);
        return ResponseUtil.success(resp);
    }

    @GetMapping("/{id}")
    public ApiResponse getMerchant(@PathVariable UUID id) {
        return ResponseUtil.success(merchantService.getMerchant(id));
    }

    @PostMapping
    public ApiResponse createMerchant(@RequestBody CreateMerchantRequest request) {
        return ResponseUtil.success(merchantService.createMerchant(request));
    }

    @PutMapping("/{id}")
    public ApiResponse updateMerchant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMerchantRequest request) {
        log.info( "id={} requet={}", id, request);
        return ResponseUtil.success(merchantService.updateMerchant(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse deleteMerchant(@PathVariable UUID id) {
        merchantService.deleteMerchant(id);
        return ResponseUtil.success("merchant deleted successfully");
    }
}
