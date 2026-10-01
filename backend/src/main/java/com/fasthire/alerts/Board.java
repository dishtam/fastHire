package com.fasthire.alerts;

import java.util.regex.Pattern;

/**
 * Job boards whose alert emails we read. Boards are recognised by the job links inside the email (not the
 * sender), so emails forwarded to a dedicated mailbox work too.
 * NOTE: the URL shapes below come from public knowledge of each site, not from real alert emails; adjust here
 * if a board's links look different.
 */
public enum Board {
    LINKEDIN("LinkedIn", Pattern.compile("^https?://([a-z]+\\.)?linkedin\\.com/(comm/)?jobs/view/(?:[^/?#]*-)?(\\d+)"), 3, null),
    BAYT("Bayt", Pattern.compile("^https?://([a-z]+\\.)?bayt\\.com/[^?#]*?/jobs/[^?#]*?-(\\d+)/?"), 2, "UAE"),
    NAUKRIGULF("Naukrigulf", Pattern.compile("^https?://([a-z]+\\.)?naukrigulf\\.com/[^?#]*?(\\d{6,})(?:$|[?#/])"), 2, "UAE"),
    NAUKRI("Naukri", Pattern.compile("^https?://([a-z]+\\.)?naukri\\.com/[^?#]*?(\\d{9,})(?:$|[?#/])"), 2, "IN");

    public final String label;
    final Pattern link;
    final int idGroup;
    /** Region to assume when the job's location says nothing, or null to use the global fallback. */
    final String defaultRegion;

    Board(String label, Pattern link, int idGroup, String defaultRegion) {
        this.label = label;
        this.link = link;
        this.idGroup = idGroup;
        this.defaultRegion = defaultRegion;
    }
}
