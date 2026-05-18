package com.kingwiredemo.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_log", indexes = {
        @Index(name = "idx_recon_run_at", columnList = "run_at"),
        @Index(name = "idx_recon_sku",    columnList = "sku")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80)
    private String sku;

    @Column(length = 50)
    private String source;

    @Column(length = 20)
    private String action;       // INSERT | UPDATE | SKIP | CONFLICT

    @Column(name = "diff_notes", columnDefinition = "TEXT")
    private String diffNotes;    // human-readable summary of what changed

    @CreationTimestamp
    @Column(name = "run_at")
    private LocalDateTime runAt;
}
