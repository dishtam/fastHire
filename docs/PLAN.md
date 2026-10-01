# fastHire: Build Plan

Single Spring Boot modular monolith + React frontend. Defaults: Java 21, Spring Boot 3, Maven, YAML profile, Telegram notifications.

## Architecture
```
Excel (India|UAE) --import--> Postgres <---- React/MUI dashboard
                                ^  ^             | REST
   Scheduler (daily) -> Scrapers +  +- Scoring -> Notifier (Telegram)
                          (per ATS)   (keyword -> Claude)
                                       Drafting (Claude) -> Resume (Claude -> OpenHTMLtoPDF)
```

## Sources, not companies
The sheet has no company names. Each row is a URL (one tab per region):
- a company career site or ATS board (possibly a saved search, so query/location params are preserved),
- or a generic job board.

Each URL becomes a `source`, classified as `ATS` (supported scraper), `BOARD_EMAIL_ONLY` (Naukri, LinkedIn, etc.; never scraped, handled by Phase 4 alert emails) or `UNSUPPORTED` (shown on the dashboard, never silently skipped). Employer name is stored per job, read from the posting.

## Data model (Flyway)
- `source`: id, region, url, label, kind, ats_type, ats_token, enabled, last_scraped_at, scrape_status
- `contact`: id, employer_name, name, linkedin_url, role_type (HIRING_MANAGER/HR/EMPLOYEE); matched to jobs by employer_name
- `job`: id, source_id, external_id, employer_name, title, location, description, url, posted_at, first_seen_at; UNIQUE(source_id, external_id)
- `job_score`: job_id, keyword_score, llm_score, reason, model, scored_at
- `job_status`: job_id, status (NEW/APPLIED/REFERRED/INTERVIEW), notes, updated_at
- `draft_message`: job_id, contact_id, type (HM/EMPLOYEE), body
- `scrape_run`: id, source_id, started_at, jobs_found, jobs_new, error

Dedup: insert with ON CONFLICT DO NOTHING; only inserted rows are scored. Secondary dedup on (employer, normalised title, location) across sources.

## Phase 1: Scraper and fit scoring
1. Scaffold: Maven, Postgres via docker-compose, Flyway, Testcontainers.
2. Excel importer (Apache POI): tab = region, column `url`, optional `label`, `enabled`. Idempotent. Bad rows reported, not fatal.
3. Source classifier from URL pattern (Greenhouse, Lever, Workday, Eightfold, known boards).
4. `JobSource` interface; scrapers: Greenhouse, Lever, then Workday, Eightfold. Rate limiting, retries, per-source error isolation, `scrape_run` records. Fixture-based tests.
5. Scoring on new jobs only: keyword filter (profile skills/titles/tags, hard filters) then Claude JSON `{score, reason}` (temperature 0, validated, cached profile prefix). Threshold configurable (default 70).
6. `@Scheduled` daily + manual run endpoint; one Telegram digest per run.
Done when: a real run produces scored jobs and a digest, and a second run finds 0 new jobs.

## Phase 2: Dashboard and messages
REST API (`/jobs`, status PATCH, source health); drafts generated at score time for jobs above threshold (HM: job ID + relevant experience + interview ask; employee: softer referral ask); React+MUI with India|UAE tabs, top 20, copy buttons, status dropdown, source health panel; single-user auth.

## Phase 3: Tailored resume
Master profile YAML with bullet ids/tags/metrics. LLM returns bullet ids, order, optional rephrasings; server validates ids exist and rephrasings add no new skills/numbers (retry on violation). Thymeleaf XHTML single-column template -> OpenHTMLtoPDF; `GET /jobs/{id}/resume.pdf` on click, cached by (job, profile hash).

## Phase 4: Alert-email parsing
IMAP reader on a dedicated label; parsers for LinkedIn, Bayt, Naukrigulf with real-email fixtures; jobs get a `source` and flow through the same scoring/drafting.

## Cross-cutting
Config via application.yml + env (ANTHROPIC_API_KEY, Telegram, DB). Keyword filter removes most jobs before any LLM call; per-run token/cost log. `profile.yml`, real sheet and `.env` are gitignored; examples committed. Parked: profile-activity bot (daily reminder in digest instead).

## Risks
Custom career sites (visible as UNSUPPORTED, build on demand); Workday/Eightfold endpoint changes (isolated classes, fixtures); LLM score drift (fixed rubric, stored reasons); resume fabrication (id-based selection + server validation); site terms (official JSON endpoints, low request rate).

## Implementation notes
- LLM provider is behind `LlmClient`; OpenAI (`OPENAI_API_KEY`, `OPENAI_MODEL`, default gpt-4o-mini) is the current implementation, replacing the Claude API assumed above.
- `POST /admin/run` runs the whole pipeline (scrape, score unscored jobs, Telegram digest); the same pipeline runs daily at 07:00 IST.
