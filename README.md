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

## Tests
`cd backend && mvn test`. Integration tests use Testcontainers, so Docker must be running; set `TEST_DB_URL`
to use an existing empty Postgres instead.
