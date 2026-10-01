package com.fasthire.alerts;

import java.util.List;
import java.util.Locale;

/** Maps a job's location text to the dashboard region (IN or UAE). */
final class RegionGuesser {
    private static final List<String> UAE = List.of("uae", "united arab emirates", "dubai", "abu dhabi",
        "sharjah", "ajman", "ras al khaimah", "fujairah", "umm al quwain", "al ain");
    private static final List<String> INDIA = List.of("india", "bengaluru", "bangalore", "hyderabad", "chennai",
        "pune", "mumbai", "delhi", "gurgaon", "gurugram", "noida", "kolkata", "ahmedabad", "kochi", "jaipur",
        "chandigarh", "coimbatore", "indore", "thiruvananthapuram", "nagpur", "lucknow");

    private RegionGuesser() {}

    static String guess(String location, String boardDefault, String globalFallback) {
        String l = location == null ? "" : location.toLowerCase(Locale.ROOT);
        for (String k : UAE) {
            if (l.contains(k)) {
                return "UAE";
            }
        }
        for (String k : INDIA) {
            if (l.contains(k)) {
                return "IN";
            }
        }
        return boardDefault != null ? boardDefault : globalFallback;
    }
}
