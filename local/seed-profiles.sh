#!/usr/bin/env bash
# Saves a profile for each of the realm's users on the local compose stack, so the CV and jobs zones have
# something to work from without filling in the form. It signs in as each user with the cv-api client's
# password grant and saves through PUT /api/profile, so the api checks each profile as it would the form's,
# and it belongs to whoever keycloak says that user is today. Run it again to put the profiles back.
#   candidate        backend, Kotlin and Kafka, London
#   candidate-other  frontend, TypeScript and React, remote
#   admin            data and platform, Python and Go, Manchester
set -euo pipefail

KEYCLOAK=${KEYCLOAK:-http://localhost:8180}
API=${API:-http://localhost:8080/api}

token() {
  curl -fsS "$KEYCLOAK/realms/skill2career/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=cv-api -d username="$1" -d password=test |
    sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}

save() {
  local user=$1 profile=$2
  curl -fsS -o /dev/null -X PUT "$API/profile" \
    -H "Authorization: Bearer $(token "$user")" -H 'Content-Type: application/json' \
    --data-binary "$profile"
  echo "Saved a profile for $user"
}

save candidate '{
  "name": "Casey Candidate",
  "targetRole": "Senior Backend Engineer",
  "yearsOfExperience": "7",
  "location": "London, UK",
  "workAuthorization": "UK citizen",
  "skills": ["Kotlin", "Java", "Spring Boot", "PostgreSQL", "Kafka", "AWS", "Docker", "Kubernetes", "REST APIs"],
  "experience": "Seven years building backend services for payments and banking. Led the move of a card settlement system from a nightly batch to event-driven services on Kafka, and designed the APIs a mobile banking app is built on. Comfortable owning a service from design through to on-call.",
  "education": "BSc Computer Science, University of Manchester, 2017",
  "projects": ["An open-source Kotlin library for idempotent Kafka consumers, used by three teams at work"],
  "certifications": ["AWS Certified Developer - Associate"],
  "languages": ["English", "Spanish"],
  "email": "casey.candidate@example.com",
  "phone": "+44 7700 900123",
  "linkedin": "https://www.linkedin.com/in/casey-candidate-example",
  "portfolio": "https://github.com/casey-candidate-example",
  "workHistory": [
    {
      "company": "Northwind Payments",
      "title": "Senior Software Engineer",
      "startDate": "2021-03",
      "bullets": [
        "Moved card settlement from a nightly batch to Kafka events, cutting settlement time by 40%",
        "Designed the ledger API behind 2 million daily transactions, in Kotlin and Spring Boot",
        "Mentored four engineers and ran the backend guild"
      ]
    },
    {
      "company": "Contoso Bank",
      "title": "Software Engineer",
      "startDate": "2017-09",
      "endDate": "2021-02",
      "bullets": [
        "Built the Java services behind the mobile app'\''s accounts and payments screens",
        "Cut p99 latency of the accounts API from 900ms to 150ms with PostgreSQL query tuning"
      ]
    }
  ]
}'

save candidate-other '{
  "name": "Robin Other",
  "targetRole": "Frontend Engineer",
  "yearsOfExperience": "4",
  "location": "Remote, UK",
  "workAuthorization": "UK settled status",
  "skills": ["TypeScript", "JavaScript", "React", "Next.js", "CSS", "Accessibility", "GraphQL", "Jest", "Playwright"],
  "experience": "Four years building accessible web apps. Rebuilt a retail checkout in React and Next.js to meet WCAG 2.2 AA, and set up the visual and end-to-end testing the team now ships behind.",
  "education": "BA Interaction Design, University of Leeds, 2020",
  "projects": ["A design system of 40 accessible React components, documented in Storybook"],
  "languages": ["English", "French"],
  "email": "robin.other@example.com",
  "portfolio": "https://robin-other.example.com",
  "workHistory": [
    {
      "company": "Fabrikam Retail",
      "title": "Frontend Engineer",
      "startDate": "2022-01",
      "bullets": [
        "Rebuilt checkout in React and Next.js, lifting conversion by 6%",
        "Brought the site to WCAG 2.2 AA, with axe checks in every pull request",
        "Added Playwright journeys for the ten busiest pages"
      ]
    },
    {
      "company": "Litware Studio",
      "title": "Junior Web Developer",
      "startDate": "2020-07",
      "endDate": "2021-12",
      "bullets": ["Built client sites in TypeScript and CSS, from design to launch"]
    }
  ]
}'

save admin '{
  "name": "Alex Admin",
  "targetRole": "Data Platform Engineer",
  "yearsOfExperience": "10",
  "location": "Manchester, UK",
  "skills": ["Python", "Go", "SQL", "Terraform", "Kubernetes", "GCP", "Airflow", "dbt"],
  "experience": "Ten years in data and platform engineering. Runs the data platform a 300-person company reports from, and moved its pipelines from cron jobs to Airflow and dbt on Kubernetes.",
  "education": "MSc Data Science, University of Edinburgh, 2015",
  "certifications": ["Google Cloud Professional Data Engineer", "Certified Kubernetes Administrator"],
  "languages": ["English"],
  "email": "alex.admin@example.com",
  "workHistory": [
    {
      "company": "Adventure Works",
      "title": "Lead Data Platform Engineer",
      "startDate": "2019-05",
      "bullets": [
        "Moved 200 pipelines from cron to Airflow and dbt, halving failed overnight loads",
        "Wrote the Go services that load event data into BigQuery, 50GB a day",
        "Manage all data infrastructure in Terraform on GCP"
      ]
    }
  ]
}'
