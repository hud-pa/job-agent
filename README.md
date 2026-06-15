# Job Agent

A personal assistant for automated job searching and intelligent evaluation of job listings.

## Current Features
- **Scraping**: Automated fetching of job listings from the `jobs.ch` portal using their internal API.
- **Database**: Storage of job listings in PostgreSQL including full descriptions and AI evaluation results.
- **AI Evaluation**: Integration with Google Gemini API to match job descriptions against candidate profiles.

## Tech Stack
- Java 17+
- Spring Boot 3
- Spring Data JPA & PostgreSQL
- Jackson (for JSON parsing)

## Getting Started
1. Set up a PostgreSQL database named `job_agent`.
2. Obtain a Gemini API key from Google AI Studio.
3. Create `src/main/resources/application-local.properties` and add your key:
   ```properties
   gemini.api.key=your_actual_api_key_here
   ```
4. Run the application with the `local` profile:
   ```bash
   ./mvnw spring-boot:run -Dspring-profiles.active=local
   ```

## API Endpoints
- `POST /api/jobs/scrape?term=java` - Triggers a search for new jobs.
- `GET /api/jobs` - Retrieves all found job listings.
- `POST /api/jobs/evaluate` - Evaluates new jobs (assigns mock AI scores).

---
*This project is currently under active development.*