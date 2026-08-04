package com.haryokuncoro.ops.service;

import com.haryokuncoro.ops.dto.FeeSummary;
import com.haryokuncoro.ops.entity.BillingOrder;
import com.haryokuncoro.ops.entity.FeeConfig;
import com.haryokuncoro.ops.entity.FeeTransaction;
import com.haryokuncoro.ops.repository.FeeConfigRepository;
import com.haryokuncoro.ops.repository.FeeTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FeeService {
    private static final int SCALE = 2;

    private final FeeConfigRepository feeConfigRepository;
    private final FeeTransactionRepository feeTransactionRepository;

    public FeeService(FeeConfigRepository feeConfigRepository, FeeTransactionRepository feeTransactionRepository) {
        this.feeConfigRepository = feeConfigRepository;
        this.feeTransactionRepository = feeTransactionRepository;
    }

    @Transactional
    public void generateOrderFees(BillingOrder order) {
        if(!feeTransactionRepository.findByOrderId(order.getId()).isEmpty()){
            feeTransactionRepository.deleteByOrderId(order.getId());
        }
        List<FeeTransaction> fees = calculateFees(order);
        feeTransactionRepository.saveAll(fees);
    }
    public List<FeeTransaction> calculateFees(BillingOrder order) {
        List<FeeConfig> configs = feeConfigRepository.findByMerchantIdAndActiveTrue(order.getMerchant().getId());
        return configs.stream()
                .map(config -> buildFeeTransaction(order, config))
                .toList();
    }

    private FeeTransaction buildFeeTransaction(BillingOrder order, FeeConfig config) {
        BigDecimal feeAmount = calculateFeeAmount(order.getAmount(), config);

        return FeeTransaction.builder()
                .order(order)
                .feeType(config.getFeeType())
                .amount(feeAmount)
                .currency(order.getCurrency())
                .description(
                        config.getFeeType().name() +
                                " calculated from config"
                )
                .build();
    }

    private BigDecimal calculateFeeAmount(BigDecimal orderAmount, FeeConfig config) {
        return orderAmount
                .multiply(config.getFeeValue())
                .divide(
                        BigDecimal.valueOf(100),
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    public FeeSummary getFeeSummary(BillingOrder order) {
        BigDecimal grossAmount = order.getAmount();
        BigDecimal totalFee = feeTransactionRepository.sumFeeByOrderId(order.getId());

        if (totalFee == null) {
            totalFee = BigDecimal.ZERO;
        }

        BigDecimal netAmount = grossAmount.subtract(totalFee);
        return FeeSummary.builder()
                .grossAmount(grossAmount)
                .totalFee(totalFee)
                .netAmount(netAmount)
                .build();
    }
}
