package com.haryokuncoro.ops.service;

import com.haryokuncoro.ops.dto.CreatePayoutJobRequest;
import com.haryokuncoro.ops.dto.CreatePayoutRequest;
import com.haryokuncoro.ops.dto.FeeSummary;
import com.haryokuncoro.ops.dto.GetPayoutResponse;
import com.haryokuncoro.ops.dto.PayoutJobEvent;
import com.haryokuncoro.ops.dto.spec.PayoutSpecification;
import com.haryokuncoro.ops.dto.StripePayoutJobEvent;
import com.haryokuncoro.ops.dto.enums.PayoutStatus;
import com.haryokuncoro.ops.entity.BillingOrder;
import com.haryokuncoro.ops.entity.Merchant;
import com.haryokuncoro.ops.entity.Payout;
import com.haryokuncoro.ops.event.producer.PayoutEventPublisher;
import com.haryokuncoro.ops.event.producer.StripePayoutEventPublisher;
import com.haryokuncoro.ops.exception.NotFoundException;
import com.haryokuncoro.ops.repository.BillingOrderRepository;
import com.haryokuncoro.ops.repository.MerchantRepository;
import com.haryokuncoro.ops.repository.PayoutRepository;
import com.haryokuncoro.ops.repository.PayoutTransactionRepository;
import com.stripe.exception.StripeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service @Slf4j
@Transactional(readOnly = true)
public class PayoutService {
    private final StripeService stripeService;
    private final FeeService feeService;
    private final MerchantRepository merchantRepository;
    private final BillingOrderRepository orderRepository;
    private final PayoutRepository payoutRepository;
    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PayoutEventPublisher publisher;
    private final StripePayoutEventPublisher stripePayoutEventPublisher;


    public PayoutService(StripeService stripeService,
                         FeeService feeService,
                         MerchantRepository merchantRepository,
                         BillingOrderRepository orderRepository,
                         PayoutRepository payoutRepository,
                         PayoutTransactionRepository payoutTransactionRepository,
                         PayoutEventPublisher publisher,
                         StripePayoutEventPublisher stripePayoutEventPublisher) {
        this.stripeService = stripeService;
        this.feeService = feeService;
        this.merchantRepository = merchantRepository;
        this.orderRepository = orderRepository;
        this.payoutRepository = payoutRepository;
        this.payoutTransactionRepository = payoutTransactionRepository;
        this.publisher = publisher;
        this.stripePayoutEventPublisher = stripePayoutEventPublisher;
    }

    @Transactional
    public Payout triggerPayout(CreatePayoutRequest request) {
        UUID merchantId = request.getMerchantId();
        LocalDate periodStart = request.getPeriodStart();
        LocalDate periodEnd = request.getPeriodEnd();
        Merchant merchant = merchantRepository.findById(merchantId).orElseThrow(
                () -> new NotFoundException("merchant not found")
        );


        Payout existingPayout = payoutRepository.findByMerchantIdAndPeriodStartAndPeriodEnd(merchantId, periodStart, periodEnd);
        if (existingPayout != null) {
            if(existingPayout.getStatus().equals(PayoutStatus.FAILED) || existingPayout.getStatus().equals(PayoutStatus.CANCELLED)){
                publishStripePayoutJob(existingPayout);
                return existingPayout;
            }
            throw new IllegalStateException("Payout already exists");
        }

        List<BillingOrder> orders = orderRepository.findEligibleForPayout(
                        merchantId,
                        periodStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                        periodEnd.plusDays(1)
                                .atStartOfDay()
                                .toInstant(ZoneOffset.UTC)
                );

        if (orders.isEmpty()) {
            throw new IllegalStateException("No eligible orders found");
        }

        BigDecimal grossAmount = BigDecimal.ZERO;
        BigDecimal payoutAmount = BigDecimal.ZERO;
        BigDecimal feeAmount = BigDecimal.ZERO;

        Payout payout = Payout.builder()
                .payoutNo(generateInternalPayoutNo())
                .merchant(merchant)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .status(PayoutStatus.PENDING)
                .currency("USD")
                .payoutAmount(BigDecimal.ZERO)
                .grossAmount(BigDecimal.ZERO)
                .feeAmount(BigDecimal.ZERO)
                .build();
        payoutRepository.saveAndFlush(payout);

        for (BillingOrder order : orders) {
            FeeSummary summary = feeService.getFeeSummary(order);
            grossAmount = grossAmount.add(summary.getGrossAmount());
            payoutAmount = payoutAmount.add(summary.getNetAmount());
            feeAmount = feeAmount.add(summary.getTotalFee());
            order.setPayout(payout);
        }

        payout.setGrossAmount(grossAmount);
        payout.setFeeAmount(feeAmount);
        payout.setPayoutAmount(payoutAmount);

        payoutRepository.saveAndFlush(payout);
        publishStripePayoutJob(payout);
        return payout;
    }

    public boolean validatePayout(UUID merchantId, LocalDate periodStart, LocalDate periodEnd){
        Payout existingPayout = payoutRepository.findByMerchantIdAndPeriodStartAndPeriodEnd(merchantId, periodStart, periodEnd);
        if(existingPayout == null) return true;

        if (existingPayout.getStatus().equals(PayoutStatus.FAILED) || existingPayout.getStatus().equals(PayoutStatus.CANCELLED) ) {
            return true;
        }
        return false;
    }

    private String generateInternalPayoutNo() {
        return "PO-" + System.currentTimeMillis();
    }

    public void publishStripePayoutJob(Payout payout) {
        StripePayoutJobEvent event = StripePayoutJobEvent.builder()
                .id(payout.getId())
                .merchantId(payout.getMerchant().getId())
                .build();
        stripePayoutEventPublisher.publish(event);
    }

    @Transactional
    public void handleStripePayoutJob(StripePayoutJobEvent event){
        Payout payout = payoutRepository.findById(event.getId()).orElseThrow(
                () -> new RuntimeException("fail to retrieve payout")
        );
        payout.setStatus(PayoutStatus.PROCESSING);
        payoutRepository.save(payout);
        String transferId = null;
        String payoutId = null;
        try {
            transferId = stripeService.transfer(payout, toCents(payout.getPayoutAmount()), payout.getCurrency(), payout.getMerchant().getStripeAccountId());
            payoutId = stripeService.payout(payout, toCents(payout.getPayoutAmount()), payout.getCurrency(), payout.getMerchant().getStripeAccountId());
        } catch (StripeException e) {
            payout.setStatus(PayoutStatus.FAILED);
            payoutRepository.save(payout);
            log.error("fail to handle stripe payout", e);
            return;
        }

        payout.setStripeTransferId(transferId);
        payout.setStripePayoutId(payoutId);
        payout.setStatus(PayoutStatus.INITIATED);
        payoutRepository.save(payout);
    }

    public long toCents(BigDecimal amount) {
        return amount
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    public void publishPayoutJobs(CreatePayoutJobRequest request) {
        List<UUID> merchants = findEligibleMerchants(request.getPeriodStart(), request.getPeriodEnd());
        if(merchants.isEmpty()){
            log.warn("fail to publish payout jobs, eligible merchant not found. request {}", request);
            throw new NotFoundException("eligible merchants not found");
        }
        for(UUID merchantId : merchants){
            LocalDate periodStart = request.getPeriodStart();
            LocalDate periodEnd = request.getPeriodEnd();
            boolean isValidPayout = validatePayout(merchantId, periodStart, periodEnd);
            if(!isValidPayout){
                log.warn("canceled publish payout job due to payout exists. merchantId : {} period {} {}",
                        merchantId, periodStart, periodEnd);
                continue;
            }
            UUID id = UUID.randomUUID();
            PayoutJobEvent event = PayoutJobEvent.builder()
                    .eventId(id)
                    .merchantId(merchantId)
                    .periodStart(request.getPeriodStart())
                    .periodEnd(request.getPeriodEnd())
                    .build();
            publisher.publish(event);
        }

    }

    @Transactional
    public void handlePayoutJobs(PayoutJobEvent event){
        log.info("handle payout jobs {}", event);
        this.triggerPayout(CreatePayoutRequest.builder()
                        .merchantId(event.getMerchantId())
                        .periodStart(event.getPeriodStart())
                        .periodEnd(event.getPeriodEnd()).build());

    }

    private List<UUID> findEligibleMerchants(LocalDate periodStart, LocalDate periodEnd){
        return orderRepository.findUniqueMerchantIdsEligibleForPayout(
                periodStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                periodEnd.plusDays(1)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
        );
    }

    public Page<GetPayoutResponse> search(
            UUID merchantId,
            String status,
            Pageable pageable) {

        Specification<Payout> spec = null;

        if (merchantId != null) {
            spec = Specification.allOf(
                    spec,
                    PayoutSpecification.hasMerchant(merchantId)
            );
        }
        if (status != null) {
            spec = Specification.allOf(
                    spec,
                    PayoutSpecification.hasStatus(PayoutStatus.valueOf(status))
            );
        }

        return payoutRepository.findAll(spec, pageable)
                .map(this::toResponse);
    }

    public GetPayoutResponse toResponse(Payout payout) {
        return GetPayoutResponse.builder()
                .id(payout.getId())
                .merchantId(payout.getMerchant().getId())
                .merchantName(payout.getMerchant().getMerchantName())
                .periodStart(payout.getPeriodStart().toString())
                .periodEnd(payout.getPeriodEnd().toString())
                .grossAmount(payout.getGrossAmount())
                .currency(payout.getCurrency())
                .feeAmount(payout.getFeeAmount())
                .payoutAmount(payout.getPayoutAmount())
                .stripePayoutId(payout.getStripePayoutId())
                .payoutDate(payout.getPayoutDate())
                .status(payout.getStatus())
                .build();
    }

    @Transactional
    public void cancelPayout(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new NotFoundException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.INITIATED || !payout.getStripePayoutId().startsWith("po")) {
            throw new IllegalStateException("Failed to cancel payout. Only INITIATED payouts can be cancelled");
        }

        try {
            stripeService.cancelPayout(payout);
        }catch (StripeException e){
            throw new IllegalStateException("Failed to cancel payout", e);
        }
        payout.setStatus(PayoutStatus.CANCELLED);
        payoutRepository.save(payout);
    }

}
