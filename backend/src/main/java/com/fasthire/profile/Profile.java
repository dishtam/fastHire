package com.fasthire.profile;

import java.util.Set;

/** Facts about the candidate. keywords() drives the pre-filter; promptText() goes to the LLM. */
public record Profile(String name, String headline, Set<String> keywords, String promptText) {}
