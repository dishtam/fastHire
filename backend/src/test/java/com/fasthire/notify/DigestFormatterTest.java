package com.fasthire.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasthire.scoring.ScoredJob;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DigestFormatterTest {
    private static ScoredJob job(int i) {
        return new ScoredJob(i, "Java Dev " + i, "Acme", "Dubai", "https://x/" + i, "UAE", 90 - i, "Good fit");
    }

    @Test void emptyDigest() {
        assertThat(DigestFormatter.format(List.of())).contains("no new matching jobs");
    }

    @Test void listsJobsWithScoreReasonAndLink() {
        String s = DigestFormatter.format(List.of(job(1)));
        assertThat(s).contains("1 new matching job\n", "89  Java Dev 1 @ Acme [UAE]", "Good fit", "https://x/1");
    }

    @Test void capsLengthAndCountsTheRest() {
        List<ScoredJob> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(job(i));
        String s = DigestFormatter.format(many);
        assertThat(s).contains("...and 25 more").hasSizeLessThanOrEqualTo(4000);
    }
}
