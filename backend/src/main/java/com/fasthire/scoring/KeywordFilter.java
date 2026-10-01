package com.fasthire.scoring;

import com.fasthire.profile.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Cheap first pass so most jobs never reach the LLM. */
@Component
public class KeywordFilter {
    private final List<String> excludeTitleWords;
    private final int minMatches;

    public KeywordFilter(
            @Value("${fasthire.scoring.exclude-title-words:intern,sales,recruiter,accountant}") List<String> excludeTitleWords,
            @Value("${fasthire.scoring.min-keyword-matches:2}") int minMatches) {
        this.excludeTitleWords = excludeTitleWords.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
        this.minMatches = minMatches;
    }

    public KeywordResult evaluate(Profile profile, String title, String description) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        for (String bad : excludeTitleWords) {
            if (!bad.isEmpty() && contains(t, bad)) {
                return new KeywordResult(0, List.of(), false);
            }
        }
        List<String> matched = new ArrayList<>();
        int titleHits = 0;
        int bodyHits = 0;
        for (String kw : profile.keywords()) {
            boolean inTitle = contains(t, kw);
            boolean inBody = contains(d, kw);
            if (inTitle || inBody) {
                matched.add(kw);
            }
            if (inTitle) {
                titleHits++;
            } else if (inBody) {
                bodyHits++;
            }
        }
        int score = Math.min(100, titleHits * 20 + bodyHits * 5);
        return new KeywordResult(score, matched, titleHits >= 1 || matched.size() >= minMatches);
    }

    /** Whole-word match that also copes with terms like c++ and node.js. */
    static boolean contains(String text, String term) {
        return Pattern.compile("(?<![a-z0-9+#])" + Pattern.quote(term) + "(?![a-z0-9+#])").matcher(text).find();
    }
}
