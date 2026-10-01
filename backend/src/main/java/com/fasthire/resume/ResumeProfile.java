package com.fasthire.resume;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** The full structured profile: the only source of facts for a tailored resume. */
public record ResumeProfile(String name, String headline, String email, String phone, String location,
                            String linkedin, String github, String summary,
                            Map<String, List<String>> skills, List<Entry> experience, List<Entry> projects,
                            List<Education> education, List<String> achievements) {

    public record Bullet(String id, String text, List<String> tags) {}

    /** A job or a project: heading is the title or project name, org the employer (empty for projects). */
    public record Entry(String heading, String org, String dates, String location, List<Bullet> bullets) {}

    public record Education(String school, String degree, String year, String cgpa) {}

    public List<Bullet> allBullets() {
        List<Bullet> out = new ArrayList<>();
        experience.forEach(e -> out.addAll(e.bullets()));
        projects.forEach(e -> out.addAll(e.bullets()));
        return out;
    }

    public List<String> allSkills() {
        List<String> out = new ArrayList<>();
        skills.values().forEach(out::addAll);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static ResumeProfile parse(String yamlText) {
        Map<String, Object> root = new Yaml().load(yamlText);
        Map<String, Object> contact = root.get("contact") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();

        Map<String, List<String>> skills = new LinkedHashMap<>();
        if (root.get("skills") instanceof Map<?, ?> sm) {
            sm.forEach((k, v) -> skills.put(String.valueOf(k), strings(v)));
        }
        List<Education> education = new ArrayList<>();
        if (root.get("education") instanceof List<?> el) {
            for (Object o : el) {
                Map<String, Object> e = (Map<String, Object>) o;
                education.add(new Education(str(e.get("school")), str(e.get("degree")), str(e.get("year")),
                    str(e.get("cgpa"))));
            }
        }
        return new ResumeProfile(str(root.get("name")), str(root.get("headline")), str(contact.get("email")),
            str(contact.get("phone")), str(contact.get("location")), str(contact.get("linkedin")),
            str(contact.get("github")), str(root.get("summary")), skills,
            entries(root.get("experience"), false), entries(root.get("projects"), true), education,
            strings(root.get("achievements")));
    }

    @SuppressWarnings("unchecked")
    private static List<Entry> entries(Object section, boolean project) {
        List<Entry> out = new ArrayList<>();
        if (!(section instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            Map<String, Object> e = (Map<String, Object>) o;
            List<Bullet> bullets = new ArrayList<>();
            if (e.get("bullets") instanceof List<?> bl) {
                for (Object b : bl) {
                    Map<String, Object> bm = (Map<String, Object>) b;
                    bullets.add(new Bullet(str(bm.get("id")), str(bm.get("text")), strings(bm.get("tags"))));
                }
            }
            out.add(project
                ? new Entry(str(e.get("name")), "", "", "", bullets)
                : new Entry(str(e.get("title")), str(e.get("company")), str(e.get("dates")),
                    str(e.get("location")), bullets));
        }
        return out;
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
