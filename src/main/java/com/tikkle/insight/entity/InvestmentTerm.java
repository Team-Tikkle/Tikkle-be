package com.tikkle.insight.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "INVESTMENT_TERMS")
public class InvestmentTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "term", nullable = false, length = 100)
    private String term;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Builder
    public InvestmentTerm(String term, String description, Integer displayOrder) {
        this.term = term;
        this.description = description;
        this.displayOrder = displayOrder;
    }
}