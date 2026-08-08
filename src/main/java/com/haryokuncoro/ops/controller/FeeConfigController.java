package com.haryokuncoro.ops.controller;


import com.haryokuncoro.ops.dto.ApiResponse;
import com.haryokuncoro.ops.dto.CreateFeeConfigRequest;
import com.haryokuncoro.ops.dto.GetFeeConfigResponse;
import com.haryokuncoro.ops.dto.UpdateFeeConfigRequest;
import com.haryokuncoro.ops.service.FeeConfigService;
import com.haryokuncoro.ops.util.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/fee-configs")
@RequiredArgsConstructor
public class FeeConfigController {

    private final FeeConfigService feeConfigService;

    @PostMapping
    public ApiResponse<GetFeeConfigResponse> create(@Valid @RequestBody CreateFeeConfigRequest request) {
        GetFeeConfigResponse resp = feeConfigService.create(request);
        return ResponseUtil.success(resp);
    }

    @PutMapping("/{id}")
    public ApiResponse<GetFeeConfigResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateFeeConfigRequest request) {
        GetFeeConfigResponse resp = feeConfigService.update(id, request);
        return ResponseUtil.success(resp);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        feeConfigService.delete(id);
        return ResponseUtil.success("fee config deleted successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<GetFeeConfigResponse> get(@PathVariable UUID id) {
        GetFeeConfigResponse resp = feeConfigService.get(id);
        return ResponseUtil.success(resp);
    }

    @GetMapping
    public ApiResponse<Page<GetFeeConfigResponse>> getAll(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<GetFeeConfigResponse> resp = feeConfigService.getAll(
                merchantId,
                active,
                pageable
        );
        return ResponseUtil.success(resp);
    }
}