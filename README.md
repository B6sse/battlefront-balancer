# Battlefront Balancer

A web app for making Star Wars Battlefront 2015 more competitive with ranked play and ratings for balanced matches.

## Tech stack

- **Backend:** Kotlin, Spring Boot 4, JPA. Gradle (Kotlin DSL), JDK 25.
- **Frontend:** React 18, TypeScript, Vite, SCSS. npm.
- **Database:** PostgreSQL 16.
- **Deploy / development:** Docker and docker-compose.

## Getting started

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

## Project structure

```
├── backend/          # Spring Boot (Kotlin)
│   ├── src/main/kotlin/no/battlefront/balancer/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── model/
│   │   └── BattlefrontBalancerApplication.kt
│   └── Dockerfile
├── frontend/          # Vite + React + TypeScript + SCSS
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── hooks/
│   │   ├── api/
│   │   ├── types/
│   │   └── styles/
│   └── Dockerfile
├── docker/
│   └── postgres/local/     # Git-ignored seed data
├── docker-compose.yml      # Full stack
```

## Ports

| Service   | Development | Docker |
|----------|-------------|--------|
| Frontend | 5173        | 80     |
| Backend  | 8080        | 8080   |
| Postgres | 5432        | 5432   |

## PWA (Add to Home Screen)

The app is set up as a **Progressive Web App**: it works as a normal website and can also be added to the home screen on iPhone and Android.

- **Android (Chrome):** Menu → “Add to Home screen” or “Install app”.
- **iPhone (Safari):** Share → “Add to Home Screen”.

The site uses a [web app manifest](frontend/public/manifest.webmanifest) and the relevant meta tags so the installed icon and name (“BF Balancer”) appear correctly.

Recommended for best cross-device support:

- Add PNG icons (e.g. 192×192 and 512×512) to `frontend/public/` and reference them in the manifest.
- Ensure `frontend/public/favicon.svg` exists (referenced by `frontend/index.html`).
