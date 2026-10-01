package com.fasthire.api;

import java.time.Instant;

/** One dashboard card: the job, its fit score and reason, status, and the two drafted messages. */
public record JobCard(long id, String title, String employer, String location, String url, String region,
                      int score, String reason, JobStatus status, Instant postedAt,
                      String hiringManagerMessage, String employeeMessage) {}
