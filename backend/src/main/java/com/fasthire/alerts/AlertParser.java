package com.fasthire.alerts;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.springframework.stereotype.Component;

/**
 * Finds the jobs in an alert email. Works from the job links (which are stable) and reads company and
 * location from the text of the card around each link (which is a best-effort heuristic). Alert emails have
 * no job description, only a short card, so {@code snippet} is whatever else the card shows.
 */
@Component
public class AlertParser {
    private static final Pattern NOISE = Pattern.compile(
        "(?i)^(view( this)? job|view|apply( now)?|easy apply|see (all )?jobs?|see more|be an early applicant|"
            + "actively (recruiting|hiring)|promoted|new|unsubscribe|manage (alerts|settings).*|"
            + "\\d+ (applicants?|days?|hours?|weeks?|minutes?)( ago)?|posted .*|\\d+\\+? ?applicants?)$");
    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://[^\\s<>\"')]+");
    private static final List<String> REDIRECT_PARAMS = List.of("url", "u", "redirect", "target", "link");
    private static final int MAX_SNIPPET_LINES = 6;

    public List<AlertJob> parse(EmailContent email) {
        List<Hit> hits = !email.html().isBlank() ? fromHtml(email.html()) : fromText(email.text());
        List<AlertJob> out = new ArrayList<>();
        for (Hit h : hits) {
            out.add(new AlertJob(h.board, h.id, h.title, h.employer, h.location, h.url, h.snippet));
        }
        return out;
    }

    private record Key(Board board, String id) {}

    private static final class Hit {
        Board board;
        String id;
        String url;
        String title;
        String employer;
        String location;
        String snippet = "";
    }

    private List<Hit> fromHtml(String html) {
        Document doc = Jsoup.parse(html);
        Map<Key, List<Element>> anchorsByJob = new LinkedHashMap<>();
        Map<Key, String> urls = new LinkedHashMap<>();
        for (Element a : doc.select("a[href]")) {
            String url = unwrap(a.attr("href"));
            for (Board b : Board.values()) {
                Matcher m = b.link.matcher(url);
                if (m.find()) {
                    Key k = new Key(b, m.group(b.idGroup));
                    anchorsByJob.computeIfAbsent(k, x -> new ArrayList<>()).add(a);
                    urls.putIfAbsent(k, canonical(url));
                    break;
                }
            }
        }

        List<Hit> hits = new ArrayList<>();
        for (var e : anchorsByJob.entrySet()) {
            Key key = e.getKey();
            List<Element> anchors = e.getValue();
            Element titleAnchor = anchors.stream().filter(a -> !isNoise(a.text())).findFirst().orElse(anchors.get(0));
            List<String> card = cardLines(titleAnchor, key, anchorsByJob);

            Hit h = new Hit();
            h.board = key.board();
            h.id = key.id();
            h.url = urls.get(key);
            String title = clean(titleAnchor.text());
            if (isNoise(title)) {
                title = card.stream().filter(l -> !isNoise(l)).findFirst().orElse("");
            }
            if (title.isBlank()) {
                continue; // a link we cannot name is not worth a row
            }
            h.title = title;
            fillDetails(h, card, title);
            hits.add(h);
        }
        return hits;
    }

    /** Plain-text emails: a URL with the line above it as the title. Company and location are not attempted. */
    private List<Hit> fromText(String text) {
        List<Hit> hits = new ArrayList<>();
        Map<Key, Boolean> seen = new LinkedHashMap<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            Matcher um = URL_IN_TEXT.matcher(lines[i]);
            while (um.find()) {
                String url = unwrap(um.group());
                for (Board b : Board.values()) {
                    Matcher m = b.link.matcher(url);
                    if (!m.find()) {
                        continue;
                    }
                    Key k = new Key(b, m.group(b.idGroup));
                    String title = "";
                    for (int j = i - 1; j >= 0 && j >= i - 3 && title.isEmpty(); j--) {
                        String cand = clean(lines[j]);
                        if (!cand.isEmpty() && !isNoise(cand) && !URL_IN_TEXT.matcher(cand).find()) {
                            title = cand;
                        }
                    }
                    if (!title.isEmpty() && seen.putIfAbsent(k, true) == null) {
                        Hit h = new Hit();
                        h.board = b;
                        h.id = k.id();
                        h.url = canonical(url);
                        h.title = title;
                        hits.add(h);
                    }
                    break;
                }
            }
        }
        return hits;
    }

    /** Text lines of the smallest block around the link that looks like one job card. */
    private List<String> cardLines(Element anchor, Key own, Map<Key, List<Element>> all) {
        Element card = anchor;
        while (card.parent() != null) {
            Element parent = card.parent();
            if (containsOtherJob(parent, own, all)) {
                break;
            }
            card = parent;
            if (lines(card).size() >= 6) {
                break;
            }
        }
        return lines(card);
    }

    private static boolean containsOtherJob(Element el, Key own, Map<Key, List<Element>> all) {
        for (var e : all.entrySet()) {
            if (!e.getKey().equals(own) && e.getValue().stream().anyMatch(a -> isInside(a, el))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInside(Element a, Element container) {
        for (Element p = a; p != null; p = p.parent()) {
            if (p == container) {
                return true;
            }
        }
        return false;
    }

    private void fillDetails(Hit h, List<String> card, String title) {
        int at = -1;
        for (int i = 0; i < card.size(); i++) {
            if (card.get(i).equalsIgnoreCase(title) || card.get(i).toLowerCase(Locale.ROOT).contains(title.toLowerCase(Locale.ROOT))) {
                at = i;
                break;
            }
        }
        List<String> rest = new ArrayList<>();
        for (int i = at + 1; i < card.size(); i++) {
            if (!isNoise(card.get(i))) {
                rest.add(card.get(i));
            }
        }
        if (!rest.isEmpty()) {
            String first = rest.get(0);
            int dot = first.indexOf(" · ");
            if (dot > 0) { // "Company · Location" on one line
                h.employer = first.substring(0, dot).trim();
                h.location = first.substring(dot + 3).trim();
                rest = rest.subList(1, rest.size());
            } else {
                h.employer = first;
                if (rest.size() > 1) {
                    h.location = rest.get(1);
                    rest = rest.subList(2, rest.size());
                } else {
                    rest = List.of();
                }
            }
        }
        h.snippet = String.join("\n", rest.subList(0, Math.min(rest.size(), MAX_SNIPPET_LINES)));
    }

    private static List<String> lines(Element el) {
        List<String> out = new ArrayList<>();
        NodeTraversor.traverse((node, depth) -> {
            if (node instanceof TextNode t) {
                String s = clean(t.text());
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }, el);
        return out;
    }

    private static boolean isNoise(String s) {
        return s == null || s.isBlank() || NOISE.matcher(s.trim()).matches();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Resolves click-tracking wrappers such as https://click.example/?url=https%3A%2F%2F... */
    static String unwrap(String href) {
        String url = href.trim();
        for (int hop = 0; hop < 2; hop++) {
            String query;
            try {
                query = URI.create(url).getRawQuery();
            } catch (IllegalArgumentException e) {
                return url;
            }
            if (query == null) {
                return url;
            }
            String inner = null;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && REDIRECT_PARAMS.contains(pair.substring(0, eq).toLowerCase(Locale.ROOT))) {
                    String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                    if (v.startsWith("http")) {
                        inner = v;
                        break;
                    }
                }
            }
            if (inner == null) {
                return url;
            }
            url = inner;
        }
        return url;
    }

    /** Scheme, host and path only: tracking parameters would make the same job look different each day. */
    static String canonical(String url) {
        try {
            URI u = URI.create(url);
            return u.getScheme() + "://" + u.getHost() + (u.getPath() == null ? "" : u.getPath());
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}
