package com.fitflow.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AgentUsageId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "day", nullable = false)
    private LocalDate day;
}
