# fastHire: Job Search Tool Spec

## Inputs
- One Excel sheet with two tabs, India and UAE. Each row is a career-site URL (a company site, an ATS board, a saved search, or a job board). Contacts (hiring manager/HR or employee) are optional.
- A master resume/profile with all real projects, skills, and metrics.

## Part 1: Scraping and fit scoring
- A scheduler runs daily (cheap, since it only processes new jobs) and scrapes every career site in both tabs.
- Scrapers are built per ATS platform (Workday, Greenhouse, Lever, etc.), not per company.
- Job IDs are stored in a database so only new postings get processed.
- Each job gets a role fit score against the resume: a quick keyword filter first, then the LLM scores the remaining jobs with a one-line reason.
- Jobs above the threshold trigger a notification (Telegram or email).
- Later: pull UAE job board listings by parsing saved-search alert emails from LinkedIn, Bayt, and Naukrigulf instead of scraping those sites.

## Part 2: Referral messages
For each matching job, the LLM drafts two messages:
- Hiring manager: job ID, experience, and a direct request to schedule an interview.
- Employee: a softer referral request.

Messages are sent manually on LinkedIn. The tool only drafts.

## Part 3: Tailored resume
- Many ATS rank candidates by keyword match rather than reject them, so the resume needs to match each job's keywords.
- Per job, the LLM selects, reorders, and rephrases bullets from the master profile to match the job description, and never adds skills the candidate does not have.
- Generated only on Download, as a single-column, ATS-friendly PDF.

## Part 4: Dashboard
- Two tabs, India | UAE, each showing the top 20 fits.
- Each card has: role, company, fit score with its reason; a Download resume button; Copy buttons for the hiring manager and employee messages; a status field New -> Applied -> Referred -> Interview.

## Part 5: Job board activity bot (parked)
A bot that logs in daily to Naukri, Instahyre, etc. to stay "active" breaks those sites' terms and risks bans. Use a daily reminder to update the profile manually instead.

## Context
Target is the UAE: better pay, better work-life balance, employer-arranged visa. Germany is on hold (Opportunity Card needs ~EUR 12,000 in a blocked account).

## Stack
Spring Boot (scheduler, scrapers, API), Postgres, Claude API (scoring and drafting), React + MUI (dashboard), OpenHTMLtoPDF (resumes).

## Build order
1. Scraper and fit scoring for both tabs
2. Dashboard with copy-ready messages
3. Resume tailoring
4. Alert-email parsing
