package com.haryokuncoro.ops.repository;

import com.haryokuncoro.ops.entity.BillingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingOrderRepository extends JpaRepository<BillingOrder, UUID>, JpaSpecificationExecutor<BillingOrder> {

    Optional<BillingOrder> findByMerchantIdAndOrderNo(UUID merchantId, String orderNo);
    @Query("""
            select bo
            from BillingOrder bo
            where bo.merchant.id = :merchantId
              and bo.paymentStatus = 'PAID'
              and bo.paidAt >= :periodStart
              and bo.paidAt < :periodEnd
              and bo.payout.id is null
            """)
    List<BillingOrder> findEligibleForPayout(
            UUID merchantId,
            Instant periodStart,
            Instant periodEnd
    );

    @Query("""
        select distinct bo.merchant.id
        from BillingOrder bo
        where bo.paymentStatus = 'PAID'
          and bo.paidAt >= :periodStart
          and bo.paidAt < :periodEnd
          and bo.payout.id is null or bo.payout.status IN ('FAILED','CANCELED')
        """)
    List<UUID> findUniqueMerchantIdsEligibleForPayout(
            Instant periodStart,
            Instant periodEnd
    );
    List<BillingOrder> findByPayoutId(UUID payoutId);

}