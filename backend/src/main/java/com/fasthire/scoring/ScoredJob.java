package com.fasthire.scoring;

public record ScoredJob(long jobId, String title, String employer, String location, String url,
                        String region, int score, String reason) {}
