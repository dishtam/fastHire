package com.fasthire.profile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.fasthire.resume.ResumeProfile;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/** Loads profile.yml, falling back to the committed dummy profile.example.yml. Loaded lazily and cached. */
@Component
public class ProfileLoader {
    private static final Logger log = LoggerFactory.getLogger(ProfileLoader.class);

    private final String configuredPath;
    private Profile cached;
    private ResumeProfile resume;
    private String hash;

    public ProfileLoader(@Value("${fasthire.profile-path:profile.yml}") String configuredPath) {
        this.configuredPath = configuredPath;
    }

    public synchronized Profile get() {
        ensureLoaded();
        return cached;
    }

    /** The full structured profile, used to build tailored resumes. */
    public synchronized ResumeProfile resume() {
        ensureLoaded();
        return resume;
    }

    /** SHA-256 of the profile file; tailored resumes are cached per (job, hash). */
    public synchronized String hash() {
        ensureLoaded();
        return hash;
    }

    private void ensureLoaded() {
        if (cached == null) {
            load();
        }
    }

    private void load() {
        List<String> candidates = List.of(configuredPath, "profile.yml", "../profile.yml",
            "../profile.example.yml", "profile.example.yml");
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.isRegularFile(p)) {
                log.info("Loading profile from {}", p.toAbsolutePath());
                try {
                    String text = Files.readString(p);
                    cached = parse(text);
                    resume = ResumeProfile.parse(text);
                    hash = sha256(text);
                    return;
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read profile " + p, e);
                }
            }
        }
        throw new IllegalStateException("No profile found; copy profile.example.yml to profile.yml");
    }

    public static Profile parse(InputStream in) {
        try {
            return parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read profile", e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Profile parse(String yamlText) {
        Map<String, Object> root = new Yaml().load(yamlText);
        Set<String> keywords = new LinkedHashSet<>();
        StringBuilder text = new StringBuilder();

        String name = str(root.get("name"));
        String headline = str(root.get("headline"));
        text.append(headline).append('\n');
        if (root.get("summary") != null) {
            text.append(str(root.get("summary"))).append('\n');
        }

        Object skills = root.get("skills");
        if (skills instanceof Map<?, ?> m) {
            text.append("Skills:\n");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                List<String> items = strings(e.getValue());
                text.append("- ").append(e.getKey()).append(": ").append(String.join(", ", items)).append('\n');
                items.forEach(i -> keywords.add(i.toLowerCase(Locale.ROOT)));
            }
        }

        for (String section : List.of("experience", "projects")) {
            Object entries = root.get(section);
            if (!(entries instanceof List<?> list)) {
                continue;
            }
            text.append(section.substring(0, 1).toUpperCase(Locale.ROOT)).append(section.substring(1)).append(":\n");
            for (Object o : list) {
                Map<String, Object> entry = (Map<String, Object>) o;
                String title = str(entry.getOrDefault("title", entry.get("name")));
                String company = str(entry.get("company"));
                text.append("- ").append(title).append(company.isEmpty() ? "" : " at " + company).append('\n');
                Object bullets = entry.get("bullets");
                if (bullets instanceof List<?> bl) {
                    for (Object b : bl) {
                        Map<String, Object> bullet = (Map<String, Object>) b;
                        text.append("  * ").append(str(bullet.get("text"))).append('\n');
                        strings(bullet.get("tags")).forEach(t -> keywords.add(t.toLowerCase(Locale.ROOT)));
                    }
                }
            }
        }
        keywords.removeIf(k -> k.length() < 2);
        return new Profile(name, headline, keywords, text.toString().trim());
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            l.forEach(i -> out.add(str(i)));
        }
        return out;
    }
}
