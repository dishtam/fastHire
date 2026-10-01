package com.fasthire.resume;

import java.util.List;
import java.util.Map;

/** What goes on the resume for one job: validated summary, skills, ordered bullet ids and their final text. */
public record Selection(String summary, List<String> skills, List<String> bulletIds, Map<String, String> text) {}
