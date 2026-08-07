package com.haryokuncoro.ops.dto.spec;

import com.haryokuncoro.ops.entity.Merchant;
import org.springframework.data.jpa.domain.Specification;


public class MerchantSpecification {

    public static Specification<Merchant> hasName(String name) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("merchantName")),
                        "%" + name.toLowerCase() + "%"
                );
    }

    public static Specification<Merchant> hasEmail(String email) {
        return (root, query, cb) ->
                cb.equal(root.get("email"), email);
    }

    public static Specification<Merchant> hasAccountId(String stripeAccountId) {
        return (root, query, cb) ->
                cb.equal(root.get("stripeAccountId"), stripeAccountId);
    }
}
