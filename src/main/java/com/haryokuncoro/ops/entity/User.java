package com.haryokuncoro.ops.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "users")
@Getter @Setter
public class User extends BaseEntity {
    private String username;
    private String email;
    private String password;
    private boolean enabled;
}