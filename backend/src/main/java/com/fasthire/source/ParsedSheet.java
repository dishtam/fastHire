package com.fasthire.source;

import java.util.List;

public record ParsedSheet(List<SourceRow> rows, List<String> errors) {}
