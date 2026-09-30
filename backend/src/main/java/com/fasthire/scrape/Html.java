package com.fasthire.scrape;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

final class Html {
    private Html() {}

    /** HTML to plain text, keeping paragraph/list line breaks. */
    static String toText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        doc.select("br").append("\\n");
        doc.select("p, li, div, h1, h2, h3, h4").prepend("\\n");
        return doc.text().replace("\\n", "\n").replaceAll("[ \\t]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
    }
}
