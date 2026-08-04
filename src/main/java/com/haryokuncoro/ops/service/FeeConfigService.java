package com.haryokuncoro.ops.service;

import com.haryokuncoro.ops.dto.CreateFeeConfigRequest;
import com.haryokuncoro.ops.dto.GetFeeConfigResponse;
import com.haryokuncoro.ops.dto.UpdateFeeConfigRequest;
import com.haryokuncoro.ops.dto.spec.FeeConfigSpecification;
import com.haryokuncoro.ops.entity.FeeConfig;
import com.haryokuncoro.ops.entity.Merchant;
import com.haryokuncoro.ops.exception.NotFoundException;
import com.haryokuncoro.ops.repository.FeeConfigRepository;
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
public class FeeConfigService {
    private final MerchantRepository merchantRepository;
    private final FeeConfigRepository repository;

    public FeeConfigService(MerchantRepository merchantRepository, FeeConfigRepository repository) {
        this.merchantRepository = merchantRepository;
        this.repository = repository;
    }

    @Transactional
    public GetFeeConfigResponse create(CreateFeeConfigRequest request){
        UUID merchantId = request.getMerchantId();
        Merchant merchant = merchantRepository.findById(merchantId).orElseThrow(() ->
                new NotFoundException("merchant not found"));
        FeeConfig feeConfig = FeeConfig.builder()
                .merchant(merchant)
                .feeType(request.getFeeType())
                .feeValue(request.getFeeValue())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .active(true)
                .build();
        feeConfig = repository.save(feeConfig);
        return toResponse(feeConfig);
    }

    private GetFeeConfigResponse toResponse(FeeConfig feeConfig){
        return GetFeeConfigResponse.builder()
                .id(feeConfig.getId())
                .merchantId(feeConfig.getMerchant().getId())
                .merchantName(feeConfig.getMerchant().getMerchantName())
                .feeValue(feeConfig.getFeeValue())
                .feeType(feeConfig.getFeeType())
                .effectiveFrom(feeConfig.getEffectiveFrom())
                .effectiveTo(feeConfig.getEffectiveTo())
                .active(feeConfig.getActive())
                .build();
    }

    @Transactional
    public GetFeeConfigResponse update(UUID id, UpdateFeeConfigRequest request){
        FeeConfig feeConfig = repository.findById(id).orElseThrow(
                () -> new NotFoundException("config not found")
        );
        feeConfig.setFeeType(request.getFeeType());
        feeConfig.setFeeValue(request.getFeeValue());
        feeConfig.setEffectiveFrom(request.getEffectiveFrom());
        feeConfig.setEffectiveTo(request.getEffectiveTo());
        feeConfig.setActive(request.getActive());
        repository.save(feeConfig);
        return toResponse(feeConfig);
    }

    @Transactional
    public void delete(UUID id){
        repository.findById(id).orElseThrow();
        repository.deleteById(id);
    }

    public GetFeeConfigResponse get(UUID id){
        FeeConfig feeConfig = repository.findById(id).orElseThrow(
                () -> new NotFoundException("config not found")
        );
        return toResponse(feeConfig);
    }

    public Page<GetFeeConfigResponse> getAll(
            UUID merchantId,
            Boolean active,
            Pageable pageable){
        Specification<FeeConfig> spec = null;

        if (merchantId != null) {
            spec = Specification.allOf(
                    spec,
                    FeeConfigSpecification.hasMerchant(merchantId)
            );
        }
        if (active != null) {
            spec = Specification.allOf(
                    spec,
                    FeeConfigSpecification.hasActive(active)
            );
        }

        return repository.findAll(spec, pageable)
                .map(this::toResponse);

    }
}
