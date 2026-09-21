# Ethics and responsible use

## What this software is

A tool that looks at aggregate weekly counts and flags weeks that are unusually
high compared with recent history. It exists to help an analyst notice something
worth a closer look.

## What it is not

- It does **not** confirm or declare an outbreak. A statistical signal is a
  prompt for review by a trained person who knows the data quality, the
  reporting context and the disease.
- It is **not** a clinical tool and must not inform decisions about an
  individual patient.
- It is **not** validated for any real programme. Measured performance is on
  synthetic data only and says nothing certain about real-world usefulness.

## Data protection

- The service is built for **aggregate** data (counts per place per week). It has
  no fields for names, identifiers, dates of birth, addresses or any other
  personal data, and it must not be extended to hold them without a documented
  privacy review.
- Small counts in small areas can still be identifying. Anyone deploying this on
  real data is responsible for suppression or aggregation rules that suit their
  legal and ethical context.
- No real field or patient data is used in development, tests or documentation.

## Known limits and risks

- **False alarms.** Every threshold trades sensitivity against false alarms.
  Frequent false alarms erode trust and lead people to ignore real ones.
- **Missed outbreaks.** Slow rises, reporting delays and sparse data can all hide
  a real event from these methods.
- **Reporting artefacts.** Changes in reporting completeness, holidays or system
  outages produce signals that are not disease.
- **Automation bias.** An alert can feel more certain than it is. Alerts carry the
  algorithm, score and threshold that produced them so a reviewer can judge them.

## Misuse

Do not use signals from this software to stigmatise a place or a community, to
withhold or redirect care, or to publish claims of an outbreak. Report concerns
through the channel in [SECURITY.md](SECURITY.md).
