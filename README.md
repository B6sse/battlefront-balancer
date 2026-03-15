# Battlefront Balancer

En nettside for å gjøre Star Wars Battlefront 2015 mer konkurransedyktig med rangert spill og rating for balanserte kamper.

## Teknologier

- **Backend:** Kotlin, Spring Boot 4, JPA. Gradle (Kotlin DSL), JDK 25.
- **Frontend:** React 18, TypeScript, Vite, SCSS. npm.
- **Database:** PostgreSQL 16.
- **Deploy/utvikling:** Docker og docker-compose.

## Kom i gang

### Utvikling (backend og frontend på host)

1. **Start kun Postgres**
   ```bash
   docker compose -f docker-compose.dev.yml up -d
   ```

2. **Start backend**
   ```bash
   ./gradlew :backend:bootRun
   ```
   Backend kjører på http://localhost:8080.

3. **Start frontend**
   ```bash
   cd frontend && npm run dev
   ```
   Frontend kjører på http://localhost:5173 og proxyer `/api` til backend.

### Alt med Docker

```bash
docker compose up -d
```

- Frontend: http://localhost (port 80)
- Backend API: http://localhost/api (via nginx) eller http://localhost:8080
- Postgres: localhost:5432 (bruker `battlefront` / passord `battlefront`, database `battlefront_balancer`)

Stopp: `docker compose down`.

## Prosjektstruktur

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
└── docker-compose.dev.yml  # Kun Postgres
```

## Porter

| Tjeneste | Utvikling | Docker |
|----------|-----------|--------|
| Frontend | 5173      | 80     |
| Backend  | 8080      | 8080   |
| Postgres | 5432      | 5432   |
