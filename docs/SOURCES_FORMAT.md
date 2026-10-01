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
