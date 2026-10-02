package com.fasthire.scrape;

/** Employer name for platforms that do not return one: the sheet label without a trailing "(note)", else a fallback. */
final class Employer {
    private Employer() {}

    static String name(SourceRef ref, String fallback) {
        String label = ref.label();
        if (label != null && !label.isBlank()) {
            String cleaned = label.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return fallback;
    }
}
