# sources.xlsx format

Two tabs named `India` and `UAE`. Header row required.

| Column | Required | Notes |
|---|---|---|
| url | yes | Career site, ATS board, saved-search URL, or job board |
| label | no | Free text for the dashboard |
| enabled | no | `false` to skip; default true |

Employer names are read from each job, not from the sheet. Job boards (e.g. naukri.com) are
recorded as `BOARD_EMAIL_ONLY` and handled by alert-email parsing later.

## Limiting a company board to one place
Global companies post jobs worldwide, but each source belongs to one tab (India or UAE). Add `?location=` to the
URL to keep only jobs whose location contains one of the terms (case-insensitive, `|` separates alternatives):

```
https://jobs.lever.co/binance?location=Dubai
https://job-boards.greenhouse.io/agoda?location=Gurugram|Gurgaon|India
```
Applies to Greenhouse and Lever sources. A job with no stated location is dropped when a filter is set.
Eightfold sources already pass their own `location` to the platform.

## Extra columns
Only `url`, `label` and `enabled` are read. Other columns (category, notes, ...) are ignored, so they are safe
to keep in the sheet for your own use.

## Platforms and their options
| Platform | Sheet URL looks like | Options |
|---|---|---|
| Greenhouse | `https://job-boards.greenhouse.io/<board>` | `?location=` |
| Lever | `https://jobs.lever.co/<company>` | `?location=` |
| Ashby | `https://jobs.ashbyhq.com/<company>` | `?location=` (applied after fetching) |
| SmartRecruiters | `https://careers.smartrecruiters.com/<Company>` | `?search=` (searched by SmartRecruiters), `?location=` |
| Workday | `https://<tenant>.wd5.myworkdayjobs.com/<Site>` | `?q=` (searched by Workday), `?location=` |
| Eightfold | `https://jobs.<company>.com/careers?query=...&location=...` | passed through as-is |

Workday tenants can hold thousands of jobs, so always give a `?q=` term (e.g. `q=java`); at most 600 jobs are read
per tenant per run. Workday shows "2 Locations" for multi-location postings, which a location filter keeps because
the real places are not known. Combine options with `&`, e.g. `...?q=java&location=Pune|Hyderabad`.
