package com.kingwiredemo.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PipelineResultDto {
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private long durationMs;

    private int erpRecordsExtracted;
    private int csvRecordsExtracted;
    private int apiRecordsExtracted;
    private int totalExtracted;

    private int inserted;
    private int updated;
    private int skipped;
    private int conflicts;

    private int elasticsearchDocumentsIndexed;

    private String status;   // SUCCESS | PARTIAL | FAILED
    private String message;
}
