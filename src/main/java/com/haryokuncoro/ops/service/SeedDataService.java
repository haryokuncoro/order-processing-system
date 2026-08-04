package com.haryokuncoro.ops.service;

import com.haryokuncoro.ops.dto.CreateOrderRequest;
import com.haryokuncoro.ops.dto.SeedResponse;
import com.haryokuncoro.ops.dto.enums.FeeType;
import com.haryokuncoro.ops.dto.enums.MerchantStatus;
import com.haryokuncoro.ops.dto.enums.PaymentStatus;
import com.haryokuncoro.ops.entity.FeeConfig;
import com.haryokuncoro.ops.entity.Merchant;
import com.haryokuncoro.ops.repository.FeeConfigRepository;
import com.haryokuncoro.ops.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class SeedDataService {

    private final MerchantRepository merchantRepository;
    private final FeeConfigRepository feeConfigRepository;
    private final OrderService orderService;

    public SeedDataService(MerchantRepository merchantRepository, FeeConfigRepository feeConfigRepository, OrderService orderService) {
        this.merchantRepository = merchantRepository;
        this.feeConfigRepository = feeConfigRepository;
        this.orderService = orderService;
    }

    @Transactional
    public SeedResponse seed() {

        if (merchantRepository.count() > 0) {
            throw new IllegalStateException("Merchant data already exists");
        }

        List<Merchant> merchants = new ArrayList<>();

        for (int i = 1; i <= 1000; i++) {

            Merchant merchant = Merchant.builder()
                    .merchantCode("MRC-%03d".formatted(i))
                    .merchantName("Merchant %d".formatted(i))
                    .email("merchant%d@test.com".formatted(i))
                    .phone("08123456" + i)
                    .stripeAccountId("acct_test000"+i)
                    .status(MerchantStatus.ACTIVE)
                    .build();

            merchants.add(merchant);
        }

        merchantRepository.saveAll(merchants);

        List<FeeConfig> feeConfigs = new ArrayList<>();

        for (Merchant merchant : merchants) {

            feeConfigs.add(
                    createFee(
                            merchant,
                            FeeType.PLATFORM_FEE,
                            BigDecimal.valueOf(2.90)
                    )
            );

            feeConfigs.add(
                    createFee(
                            merchant,
                            FeeType.STRIPE_FEE,
                            BigDecimal.valueOf(1.50)
                    )
            );

            feeConfigs.add(
                    createFee(
                            merchant,
                            FeeType.TAX,
                            BigDecimal.valueOf(0.50)
                    )
            );
        }

        feeConfigRepository.saveAll(feeConfigs);

        return new SeedResponse(
                merchants.size(),
                feeConfigs.size()
        );
    }

    @Transactional
    public void seedOrderData(){
        List<Merchant> merchants = merchantRepository.findAll();
        for (Merchant merchant : merchants) {
            Random random = new Random();
            BigDecimal amount = BigDecimal.valueOf(10.0);
            for(int i=1;i<=25;i++){
                String orderNo = "0001"+i;
                Long number = random.nextLong(5, 20);
                amount = amount.add(BigDecimal.valueOf(number));
                String paymentIntentId = "pi_test1" + merchant.getMerchantCode() +"0002"+i;
                orderService.publishOrder(CreateOrderRequest.builder()
                        .merchantId(merchant.getId())
                        .orderNo(orderNo)
                        .amount(amount)
                        .paidAt(Instant.now().toString())
                        .paymentStatus(PaymentStatus.PAID)
                        .currency("USD")
                        .stripePaymentIntentId(paymentIntentId)
                        .build());
            }
        }
    }

    private FeeConfig createFee(
            Merchant merchant,
            FeeType feeType,
            BigDecimal feeValue
    ) {

        return FeeConfig.builder()
                .merchant(merchant)
                .feeType(feeType)
                .feeValue(feeValue)
                .active(true)
                .effectiveFrom(Instant.now())
                .build();
    }
}