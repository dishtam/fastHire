# fastHire

Daily job scraper with fit scoring, drafted referral messages and a dashboard. See `docs/SPEC.md` and `docs/PLAN.md`.

## Setup
1. `copy .env.example .env` and fill in `OPENAI_API_KEY` (and Telegram values if you want the digest). No quotes.
2. `copy profile.example.yml profile.yml` and replace the dummy content with your real profile (gitignored).
3. Put your sources in an Excel file with `India` and `UAE` tabs (see `docs/SOURCES_FORMAT.md`).
4. `docker compose up -d` (Postgres on port 5433).

## Run
```
cd backend
mvn spring-boot:run          # http://localhost:8081
```
```
cd frontend
npm install
npm run dev                  # http://localhost:5173 (proxies /api to 8081)
```

## Use
```
curl.exe -F "file=@sources.xlsx" localhost:8081/admin/sources/import   # load sources
curl.exe -X POST localhost:8081/admin/run                              # scrape, score, draft, notify
```
The same pipeline runs daily at 07:00 IST. Settings live in `.env` (see `.env.example`); `SCORING_THRESHOLD` sets
the minimum fit score for the digest and for drafting messages (default 70).

## Job-board alert emails (LinkedIn, Bayt, Naukrigulf, Naukri)
Boards are never scraped; the app reads the alert emails they send you instead.
1. On each board, save your searches with email alerts on (e.g. "Java Developer, Dubai").
2. In Gmail, make a label (e.g. `fastHire-alerts`) and a filter that applies it to mail from those boards.
   Consider forwarding the alerts to a dedicated mailbox rather than giving the app access to your main one.
3. Turn on IMAP, create an app password, and put the settings in `.env`:
   `MAIL_HOST=imap.gmail.com`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FOLDER=fastHire-alerts`.
   The app only reads (read-only IMAP) and remembers which emails it has processed.
4. `curl.exe -X POST localhost:8081/admin/alerts/ingest` reads new alerts now; the daily pipeline does it too.
   Jobs then go through the same scoring, drafting and dashboard as any other job.

To check how a real alert email is read, without storing anything, save it as `.eml` and run
`curl.exe -F "file=@alert.eml" localhost:8081/admin/alerts/preview`. If jobs are missing or the company/location
are wrong, the app log warns "No jobs found in email ..." and the `.eml` is what is needed to fix the parser.
Alert emails contain only a short card (title, company, location, sometimes experience and skills), not the full
job description, so fit scores and tailored resumes for these jobs rest on less information.

## Tailored resume
The dashboard's **Download resume** button builds a single-column, ATS-friendly PDF for that job from your
`profile.yml`. The model only chooses and orders your existing bullets and may lightly rephrase them; every
rewrite is checked against the original and discarded if it adds a skill, technology or number the original
did not contain. The selection is cached per (job, profile version), so repeat downloads are instant.
Put real URLs in `contact.linkedin` / `contact.github` to have them printed (placeholder words are skipped).

## Tests
`cd backend && mvn test`. Integration tests use Testcontainers, so Docker must be running; set `TEST_DB_URL`
to use an existing empty Postgres instead.
