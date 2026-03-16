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
   docker compose -f docker-compose.dev.yml up -d
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
│   └── postgres/init.sql
├── docker-compose.yml      # Full stack
└── docker-compose.dev.yml  # Postgres only
```

## Ports

| Service   | Development | Docker |
|----------|-------------|--------|
| Frontend | 5173        | 80     |
| Backend  | 8080        | 8080   |
| Postgres | 5432        | 5432   |
