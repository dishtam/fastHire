package com.fasthire.resume;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faithfulness check for rewritten text. A rewrite may reorder and rephrase, but must not introduce a
 * technology, skill, acronym or number that the source text does not contain. Anything uncertain fails,
 * and callers then fall back to the original wording.
 */
final class RewriteGuard {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+#./&'-]*");
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern PART_SPLIT = Pattern.compile("[/.&'-]");

    private RewriteGuard() {}

    /** Bullet rewrite: allowed vocabulary is the original bullet plus its tags. */
    static boolean faithfulBullet(String original, List<String> tags, String rewrite) {
        if (rewrite == null || rewrite.isBlank() || rewrite.length() > original.length() * 1.3 + 20) {
            return false;
        }
        return unsupported(rewrite, vocabulary(original + " " + String.join(" ", tags)), numbers(original)).isEmpty();
    }

    /** Summary rewrite: allowed vocabulary is everything true about the candidate. */
    static boolean faithfulSummary(String rewrite, String allProfileText) {
        if (rewrite == null || rewrite.isBlank() || rewrite.length() > 600) {
            return false;
        }
        return unsupported(rewrite, vocabulary(allProfileText), numbers(allProfileText)).isEmpty();
    }

    /** Terms or numbers in {@code text} that the allowed sources do not support. */
    static List<String> unsupported(String text, Set<String> allowedWords, Set<String> allowedNumbers) {
        List<String> bad = new java.util.ArrayList<>();
        for (String n : numbers(text)) {
            if (!allowedNumbers.contains(n)) {
                bad.add(n);
            }
        }
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String token = clean(m.group());
            if (token.isEmpty() || startsSentence(text, m.start()) || !technical(token)) {
                continue;
            }
            if (!covered(token, allowedWords)) {
                bad.add(token);
            }
        }
        return bad;
    }

    static Set<String> vocabulary(String text) {
        Set<String> words = new HashSet<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String token = clean(m.group()).toLowerCase(Locale.ROOT);
            words.add(token);
            for (String part : PART_SPLIT.split(token)) {
                if (!part.isEmpty()) {
                    words.add(part);
                }
            }
        }
        return words;
    }

    static Set<String> numbers(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            out.add(m.group().replace(",", ""));
        }
        return out;
    }

    private static boolean covered(String token, Set<String> allowed) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (allowed.contains(lower)) {
            return true;
        }
        for (String part : PART_SPLIT.split(lower)) {
            if (!part.isEmpty() && !allowed.contains(part)) {
                return false;
            }
        }
        return true;
    }

    /** Capitalised mid-sentence words, acronyms, and anything with digits or symbols look like technologies. */
    private static boolean technical(String token) {
        for (char c : token.toCharArray()) {
            if (Character.isUpperCase(c) || Character.isDigit(c) || c == '+' || c == '#') {
                return true;
            }
        }
        return false;
    }

    private static boolean startsSentence(String text, int start) {
        String before = text.substring(0, start).stripTrailing();
        return before.isEmpty() || before.endsWith(".") || before.endsWith("!") || before.endsWith("?")
            || before.endsWith(":") || before.endsWith("(");
    }

    private static String clean(String token) {
        int end = token.length();
        while (end > 0 && ".,/&'-".indexOf(token.charAt(end - 1)) >= 0) {
            end--;
        }
        return token.substring(0, end);
    }
}
