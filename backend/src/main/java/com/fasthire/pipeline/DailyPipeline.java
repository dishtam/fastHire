package com.fasthire.pipeline;

import com.fasthire.notify.DigestFormatter;
import com.fasthire.notify.Notifier;
import com.fasthire.scoring.ScoredJob;
import com.fasthire.scoring.ScoringService;
import com.fasthire.scrape.ScrapeService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** scrape -> score new jobs -> notify. One run at a time. */
@Component
public class DailyPipeline {
    private static final Logger log = LoggerFactory.getLogger(DailyPipeline.class);

    public record Result(int newJobs, int matches) {}

    private final ScrapeService scrape;
    private final ScoringService scoring;
    private final Notifier notifier;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DailyPipeline(ScrapeService scrape, ScoringService scoring, Notifier notifier) {
        this.scrape = scrape;
        this.scoring = scoring;
        this.notifier = notifier;
    }

    @Scheduled(cron = "${fasthire.schedule.cron:0 0 7 * * *}", zone = "Asia/Kolkata")
    public void scheduledRun() {
        run();
    }

    public Result run() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A run is already in progress");
        }
        try {
            List<Long> newIds = scrape.scrapeAll();
            List<ScoredJob> hits = scoring.scoreJobs(scoring.unscoredJobIds());
            try {
                notifier.send(DigestFormatter.format(hits));
            } catch (Exception e) {
                log.warn("Notification failed: {}", e.getMessage());
            }
            return new Result(newIds.size(), hits.size());
        } finally {
            running.set(false);
        }
    }
}
