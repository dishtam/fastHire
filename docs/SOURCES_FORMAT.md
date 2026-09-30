# sources.xlsx format

Two tabs named `India` and `UAE`. Header row required.

| Column | Required | Notes |
|---|---|---|
| url | yes | Career site, ATS board, saved-search URL, or job board |
| label | no | Free text for the dashboard |
| enabled | no | `false` to skip; default true |

Employer names are read from each job, not from the sheet. Job boards (e.g. naukri.com) are
recorded as `BOARD_EMAIL_ONLY` and handled by alert-email parsing later.
