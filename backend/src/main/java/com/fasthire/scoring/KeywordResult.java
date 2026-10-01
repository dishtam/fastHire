package com.fasthire.scoring;

import java.util.List;

public record KeywordResult(int score, List<String> matched, boolean pass) {}
