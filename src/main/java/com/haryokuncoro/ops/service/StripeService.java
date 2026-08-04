package com.haryokuncoro.ops.service;

import com.haryokuncoro.ops.dto.enums.PayoutStatus;
import com.haryokuncoro.ops.dto.enums.TransactionType;
import com.haryokuncoro.ops.entity.PayoutTransaction;
import com.haryokuncoro.ops.exception.NotFoundException;
import com.haryokuncoro.ops.repository.PayoutRepository;
import com.haryokuncoro.ops.repository.PayoutTransactionRepository;
import com.haryokuncoro.ops.stripe.StripeKeyResolver;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Payout;
import com.stripe.model.Transfer;
import com.stripe.net.ApiResource;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PayoutCreateParams;
import com.stripe.param.TransferCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import static com.haryokuncoro.ops.dto.enums.PayoutStatus.PAID;
import static com.haryokuncoro.ops.dto.enums.PayoutStatus.TRANSFERRED;


@Service @Slf4j
@RequiredArgsConstructor
@Transactional
public class StripeService {
    @Value("${stripe.skipSignatureCheck}")
    private boolean skipSignatureCheck;

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PayoutRepository payoutRepository;
    private final StripeKeyResolver stripeKeyResolver;

    public String transfer(com.haryokuncoro.ops.entity.Payout payout, Long amount, String currency, String destinationAccount) throws StripeException {
        String apiKey = stripeKeyResolver.resolveApiKey(currency);
        RequestOptions options = RequestOptions.builder().setApiKey(apiKey).build();

        String idempotencyKey = "transfer-" + payout.getId() + "-" + destinationAccount + "-" + amount;
        TransferCreateParams params = TransferCreateParams.builder()
                        .setAmount(amount)
                        .setCurrency(currency)
                        .setDestination(destinationAccount)
                        .build();

        RequestOptions optionsWithIdempotency = options.toBuilder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        Transfer transfer;
        BigDecimal dollarAmount = BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        try {
            transfer = Transfer.create(params, optionsWithIdempotency);
        } catch (StripeException e) {
            log.error("Transfer failed. payoutId={}, destination={}, amount={}, code={}, message={}",
                    payout.getId(), destinationAccount, amount, e.getCode(), e.getMessage(), e
            );

            PayoutTransaction failedTransaction = PayoutTransaction.builder()
                    .transactionType(TransactionType.TRANSFER)
                    .payout(payout)
                    .referenceId(null)
                    .amount(dollarAmount)
                    .status(PayoutStatus.FAILED)
                    .metadata(e.getMessage())
                    .build();
            payoutTransactionRepository.save(failedTransaction);
            throw e;
        }

        PayoutTransaction payoutTransaction = PayoutTransaction.builder()
                .transactionType(TransactionType.TRANSFER)
                .payout(payout)
                .referenceId(transfer.getId())
                .amount(dollarAmount)
                .status(TRANSFERRED)
                .build();
        payoutTransactionRepository.save(payoutTransaction);
        return transfer.getId();
    }

    @Transactional
    public String payout(com.haryokuncoro.ops.entity.Payout payoutEntity, Long amount, String currency, String connectedAccountId) throws StripeException {
        String apiKey = stripeKeyResolver.resolveApiKey(currency);
        String idempotencyKey = "payout-" + payoutEntity.getId() + "-" + connectedAccountId + "-" + amount;
        RequestOptions options = RequestOptions.builder()
                .setApiKey(apiKey)
                .setStripeAccount(connectedAccountId)
                .setIdempotencyKey(idempotencyKey)
                .build();

        PayoutCreateParams params = PayoutCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency)
                .build();

        BigDecimal dollarAmount = BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        Payout stripePayout;
        try {
            stripePayout = Payout.create(params, options);
        } catch (StripeException e) {
            log.error(
                    "Payout failed. payoutEntityId={}, account={}, amount={}, code={}, message={}",
                    payoutEntity.getId(), connectedAccountId, amount, e.getCode(), e.getMessage(), e
            );

            PayoutTransaction failedTransaction = PayoutTransaction.builder()
                    .transactionType(TransactionType.PAYOUT)
                    .payout(payoutEntity)
                    .referenceId(null)
                    .amount(dollarAmount)
                    .status(PayoutStatus.FAILED)
                    .metadata(e.getMessage())
                    .build();
            payoutTransactionRepository.save(failedTransaction);

            throw e;
        }

        String refId = stripePayout.getId();
        PayoutTransaction payoutTransaction = PayoutTransaction.builder()
                .transactionType(TransactionType.PAYOUT)
                .payout(payoutEntity)
                .referenceId(refId)
                .amount(dollarAmount)
                .status(PAID)
                .build();
        payoutTransactionRepository.save(payoutTransaction);

        return refId;
    }

    @Transactional
    public void cancelPayout(com.haryokuncoro.ops.entity.Payout payoutEntity) throws StripeException {
        String apiKey = stripeKeyResolver.resolveApiKey(payoutEntity.getCurrency());
        String connectedAccountId = payoutEntity.getMerchant().getStripeAccountId();
        RequestOptions options = RequestOptions.builder()
                .setApiKey(apiKey)
                .setStripeAccount(connectedAccountId)
                .build();
        Payout stripePayout;
        try {
            stripePayout = Payout.retrieve(payoutEntity.getStripePayoutId(), options);
            stripePayout.cancel();
            PayoutTransaction failedTransaction = PayoutTransaction.builder()
                    .transactionType(TransactionType.PAYOUT)
                    .payout(payoutEntity)
                    .referenceId(stripePayout.getId())
                    .amount(payoutEntity.getPayoutAmount())
                    .status(PayoutStatus.CANCELLED)
                    .build();
            payoutTransactionRepository.save(failedTransaction);
        } catch (StripeException e) {
            log.error(
                    "Failed to cancel Payout. payoutEntityId={}, account={}, amount={}, code={}, message={}",
                    payoutEntity.getId(), connectedAccountId, payoutEntity.getPayoutAmount(), e.getCode(), e.getMessage(), e
            );
            throw e;
        }

    }

    @Transactional
    public void handleWebhook(String payload, String signature, String webhookSecret) {

        Event event;
        if (skipSignatureCheck) {
            event = ApiResource.GSON.fromJson(payload, Event.class);
        } else {
            try {
                event = Webhook.constructEvent(payload, signature, webhookSecret);
            } catch (SignatureVerificationException ex) {
                log.error("invalid webhook signature", ex);
                throw new RuntimeException("Invalid webhook signature", ex);
            }
        }

        switch (event.getType()) {
            case "payout.paid" ->
                    handlePayoutPaid(event);
            case "payout.failed" ->
                    handlePayoutFailed(event);
            default ->
                    log.info("Ignoring event {}", event.getType() );
        }
    }

    private void handlePayoutPaid(Event event) {
        handlePayoutStatusUpdate(event, PayoutStatus.PAID);
    }

    private void handlePayoutFailed(Event event) {
        handlePayoutStatusUpdate(event, PayoutStatus.FAILED);
    }

    private void handlePayoutStatusUpdate(Event event, PayoutStatus status) {
        Payout stripePayout;
        try {
            stripePayout = (Payout) event
                    .getDataObjectDeserializer()
                    .deserializeUnsafe();
        } catch (Exception e) {
            log.error("fail to deserialize payout object", e);
            return;
        }

        String stripePayoutId = stripePayout.getId();
        com.haryokuncoro.ops.entity.Payout payout = payoutRepository.findByStripePayoutId(stripePayoutId)
                .orElseThrow(() -> new NotFoundException("payout not found"));

        payout.setStatus(status);
        payout.setPayoutDate(Instant.now());
        payoutRepository.save(payout);
    }
}
