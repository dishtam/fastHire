package com.fasthire.alerts;

/** One job found in an alert email. Alert emails carry only a card's worth of text, so snippet is short. */
public record AlertJob(Board board, String externalId, String title, String employer, String location,
                       String url, String snippet) {}
