package com.haryokuncoro.ops.service;


import com.haryokuncoro.ops.dto.enums.MerchantStatus;
import com.haryokuncoro.ops.dto.spec.MerchantSpecification;
import com.haryokuncoro.ops.entity.Merchant;
import com.haryokuncoro.ops.exception.BadRequestException;
import com.haryokuncoro.ops.exception.NotFoundException;
import com.haryokuncoro.ops.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public Page<Merchant> getAllMerchants(
            String name, String email, String stripeAccountId,
            Pageable pageable) {

        Specification<Merchant> spec = null;

        if (name != null) {
            spec = Specification.allOf(
                    spec,
                    MerchantSpecification.hasName(name)
            );
        }

        if (email != null) {
            spec = Specification.allOf(
                    spec,
                    MerchantSpecification.hasEmail(email)
            );
        }

        if (stripeAccountId != null) {
            spec = Specification.allOf(
                    spec,
                    MerchantSpecification.hasAccountId(stripeAccountId)
            );
        }

        return merchantRepository.findAll(spec, pageable);

    }

    public Merchant getMerchant(UUID id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Merchant not found"));
    }

    @Transactional
    public Merchant createMerchant(Merchant request) {

        if (merchantRepository.existsByMerchantCode(request.getMerchantCode())) {
            throw new BadRequestException("Merchant code already exists");
        }

        Merchant merchant = Merchant.builder()
                .merchantCode(request.getMerchantCode())
                .merchantName(request.getMerchantName())
                .stripeAccountId(request.getStripeAccountId())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(request.getStatus() != null
                        ? request.getStatus()
                        : MerchantStatus.ACTIVE)
                .build();

        return merchantRepository.save(merchant);
    }

    @Transactional
    public Merchant updateMerchant(UUID id, Merchant request) {

        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Merchant not found"));

        if (!merchant.getMerchantCode().equals(request.getMerchantCode())
                && merchantRepository.existsByMerchantCode(request.getMerchantCode())) {
            throw new BadRequestException("Merchant code already exists");
        }

        merchant.setMerchantCode(request.getMerchantCode());
        merchant.setMerchantName(request.getMerchantName());
        merchant.setStripeAccountId(request.getStripeAccountId());
        merchant.setEmail(request.getEmail());
        merchant.setPhone(request.getPhone());
        merchant.setStatus(request.getStatus());

        return merchantRepository.save(merchant);
    }

    @Transactional
    public void deleteMerchant(UUID id) {

        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Merchant not found"));

        merchantRepository.delete(merchant);
    }
}