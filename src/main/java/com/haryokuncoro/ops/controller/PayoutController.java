package com.haryokuncoro.ops.controller;

import com.haryokuncoro.ops.dto.ApiResponse;
import com.haryokuncoro.ops.dto.CreatePayoutJobRequest;
import com.haryokuncoro.ops.dto.CreatePayoutRequest;
import com.haryokuncoro.ops.dto.GetPayoutResponse;
import com.haryokuncoro.ops.service.PayoutService;
import com.haryokuncoro.ops.util.ResponseUtil;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {
    private final PayoutService payoutService;

    @PostMapping
    public ApiResponse create(@RequestBody CreatePayoutRequest request) {
        payoutService.triggerPayout(request);
        return ResponseUtil.success("finished payout");
    }

    @PostMapping("/jobs")
    public ApiResponse publishPayoutJobs(@RequestBody CreatePayoutJobRequest request) {
        payoutService.publishPayoutJobs(request);
        return ResponseUtil.success("finished publish payout jobs");
    }

    @GetMapping
    public ApiResponse getPayouts(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<GetPayoutResponse> resp = payoutService.search(
                merchantId,
                status,
                pageable);
        return ResponseUtil.success(resp);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse cancelPayout(@PathVariable UUID id) {
        payoutService.cancelPayout(id);
        return ResponseUtil.success("payout cancelled successfully");
    }
}
