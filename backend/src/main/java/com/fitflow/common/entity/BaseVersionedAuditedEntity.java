package com.fitflow.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseVersionedAuditedEntity extends BaseAuditedEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;
}
