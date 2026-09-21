# Outbreak Signal Service

Spring Boot service that pulls weekly surveillance data from DHIS2, flags
statistical aberrations (EARS, CUSUM), and publishes alerts as REST, signed
webhooks and FHIR `DetectedIssue`.

> **An alert is a statistical signal for a person to review. It is not a
> confirmed outbreak.**

Work in progress. Full documentation arrives with the first phases.
