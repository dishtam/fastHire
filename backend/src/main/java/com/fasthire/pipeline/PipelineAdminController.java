package com.fasthire.pipeline;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PipelineAdminController {
    private final DailyPipeline pipeline;

    public PipelineAdminController(DailyPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /** Manual run of the whole daily pipeline: scrape, score, notify. */
    @PostMapping("/admin/run")
    public DailyPipeline.Result run() {
        return pipeline.run();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String busy(IllegalStateException e) {
        return e.getMessage();
    }
}
