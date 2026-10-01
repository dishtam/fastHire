package com.fasthire.drafting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasthire.llm.LlmClient;
import com.fasthire.profile.Profile;
import java.io.IOException;
import org.springframework.stereotype.Component;

/** Drafts the two LinkedIn messages for a job in one LLM call. Messages are only drafted, never sent. */
@Component
public class MessageDrafter {
    static final int MAX_HM_CHARS = 700;
    static final int MAX_EMPLOYEE_CHARS = 550;
    static final int MAX_DESCRIPTION_CHARS = 5000;
    static final String SYSTEM = """
        You write short LinkedIn outreach messages for a job seeker. Use ONLY facts from the candidate \
        profile; never invent experience, skills, employers, projects or numbers. Plain text, no markdown, \
        no placeholders like [Name].
        Reply with JSON only: {"hiring_manager": "<message>", "employee": "<message>"}.
        hiring_manager: for the recruiter or hiring manager. Include the job title and the job reference, \
        one or two specific facts from the profile that fit the job, and a direct request to schedule an \
        interview or a short call. Max 650 characters.
        employee: for someone who already works at the company. Softer: a brief introduction, mention the \
        opening (title and job reference), and ask if they would be open to referring you or sharing advice. \
        Max 500 characters.
        Greet by first name only if the job description names a recruiter or hiring contact; otherwise start \
        with "Hi,". Sign off with the candidate's first name.""";

    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    public MessageDrafter(LlmClient llm) {
        this.llm = llm;
    }

    /** @param jobRef the ID candidates see on the posting, or the posting URL when the platform has none. */
    public DraftPair draft(Profile profile, String title, String employer, String location,
                           String jobRef, String description) throws IOException {
        String user = "CANDIDATE PROFILE\nName: " + profile.name() + "\n" + profile.promptText()
            + "\n\nJOB\nTitle: " + title + "\nEmployer: " + nz(employer) + "\nLocation: " + nz(location)
            + "\nJob reference: " + jobRef + "\nDescription:\n" + truncate(nz(description));
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return parse(llm.completeJson(SYSTEM, user));
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    DraftPair parse(String reply) throws IOException {
        JsonNode n = mapper.readTree(reply);
        String hm = n.path("hiring_manager").asText("").trim();
        String emp = n.path("employee").asText("").trim();
        if (hm.isEmpty() || emp.isEmpty()) {
            throw new IOException("Draft reply is missing a message: " + reply);
        }
        if (hm.length() > MAX_HM_CHARS || emp.length() > MAX_EMPLOYEE_CHARS) {
            throw new IOException("Draft too long (" + hm.length() + "/" + emp.length() + " chars)");
        }
        return new DraftPair(hm, emp);
    }

    private static String truncate(String s) {
        return s.length() > MAX_DESCRIPTION_CHARS ? s.substring(0, MAX_DESCRIPTION_CHARS) : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
