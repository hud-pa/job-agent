# Job Agent

A personal assistant for automated job searching and intelligent evaluation of job listings.

## Current Features
- **Scraping**: Automated fetching of job listings from the `jobs.ch` portal using their internal API.
- **Database**: Storage of job listings in PostgreSQL (title, company, location, URL).
- **Evaluation**: A system for processing new listings (currently using mock scores, ready for Gemini AI integration).

## Tech Stack
- Java 17+
- Spring Boot 3
- Spring Data JPA & PostgreSQL
- Jackson (for JSON parsing)

## Getting Started
1. Set up a PostgreSQL database named `job_agent`.
2. Update `src/main/resources/application.properties` with your credentials.
3. Run the application using `./mvnw spring-boot:run`.

## API Endpoints
- `POST /api/jobs/scrape?term=java` - Triggers a search for new jobs.
- `GET /api/jobs` - Retrieves all found job listings.
- `POST /api/jobs/evaluate` - Evaluates new jobs (assigns mock AI scores).

---
*This project is currently under active development.*