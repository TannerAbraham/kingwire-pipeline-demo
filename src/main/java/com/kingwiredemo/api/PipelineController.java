package com.kingwiredemo.api;

import com.kingwiredemo.model.dto.PipelineResultDto;
import com.kingwiredemo.pipeline.EtlPipelineRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Manual pipeline trigger endpoint.
 * Useful for development, testing, and operational re-runs without
 * waiting for the nightly cron schedule.
 */
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final EtlPipelineRunner pipelineRunner;

    /**
     * Triggers a full ETL pipeline run synchronously.
     * Returns a detailed result summary including record counts and timing.
     *
     * Note: for a production system with large data volumes, this should
     * be made async (return a job ID + status polling endpoint) to avoid
     * HTTP timeout issues. For this demo/reporting scale, sync is appropriate.
     */
    @PostMapping("/run")
    public ResponseEntity<PipelineResultDto> runPipeline() {
        PipelineResultDto result = pipelineRunner.run();
        return "FAILED".equals(result.getStatus())
                ? ResponseEntity.internalServerError().body(result)
                : ResponseEntity.ok(result);
    }
}
