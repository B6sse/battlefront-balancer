# Battlefront Balancer

A web app for making Star Wars Battlefront 2015 more competitive with ranked play and ratings for balanced matches. Check it out here:

https://battlefront-balancer.eu/ 

## Tech stack

- **Backend:** Kotlin, Spring Boot 4, JPA. Gradle (Kotlin DSL), JDK 25.
- **Frontend:** React 18, TypeScript, Vite, SCSS. npm.
- **Database:** PostgreSQL 16.
- **Deploy / development:** Docker and docker-compose.

## Getting started

Configuration comes from `backend/.env` (git-ignored). Start from the template: `cp backend/.env.example backend/.env`.

### Development (backend and frontend on host)

1. **Start Postgres only**
   ```bash
   docker compose up -d postgres
   ```

2. **Start backend**
   ```bash
   ./gradlew :backend:bootRun
   ```
   Backend runs at http://localhost:8080.

3. **Start frontend**
   ```bash
   cd frontend && npm run dev
   ```
   Frontend runs at http://localhost:5173 and proxies `/api` to the backend.

### Full stack with Docker

```bash
docker compose up -d
```

- Frontend: http://localhost (port 80)
- Backend API: http://localhost/api (via nginx) or http://localhost:8080
- Postgres: localhost:5432 (user `battlefront`, password `battlefront`, database `battlefront_balancer`)

Stop: `docker compose down`.

### Production

See [deploy/README.md](deploy/README.md): Docker Compose on a VPS with Caddy for HTTPS
(`docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`).

### Database schema and seed data

The schema is managed by **Flyway**. Migrations live in `backend/src/main/resources/db/migration/`
(`V1__schema.sql`, `V2__...`) and run automatically when the backend starts. To change the schema, add a new
`V<n>__description.sql` file; never edit a migration that has already run.

Seed data (players, matches, users) is kept out of git because it contains user credentials. Place it in
`docker/postgres/local/seed.sql` (git-ignored) and load it once, after the backend has started and created the schema:

```bash
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/postgres/local/seed.sql
```

## Code quality (ktlint + kover)

The backend uses:

- **ktlint**: Kotlin formatting/style checks
- **kover**: Kotlin test coverage reports

### Run style checks (ktlint)

```bash
./gradlew :backend:ktlintCheck
```

### Auto-format Kotlin (ktlint)

```bash
./gradlew :backend:ktlintFormat
```

### Run tests with coverage (kover)

```bash
./gradlew :backend:test :backend:koverHtmlReport
```

- **HTML report**: `backend/build/reports/kover/html/index.html`
- (Optional) XML report: `./gradlew :backend:koverXmlReport`
