# Pixel Tech backend

Spring Boot **REST API** for Pixel Tech (electronics, mobile, laptop). Runs with **Docker** against **Neon PostgreSQL**.

Use a **separate Neon database** from Aura Fresh so product/user data does not collide.

## Requirements

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine + Compose)
- A [Neon](https://neon.tech) PostgreSQL database (create a new branch/DB named e.g. `pixeltech`)

## Configuration

1. Create a Neon project/database and copy the connection string (`postgresql://…?sslmode=require`).
2. Copy the env template and fill in values:

```bash
cp .env.example .env
# Edit .env — set DATABASE_URL (Pixel Tech DB only), JWT_SECRET, etc.
```

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` | Neon connection string — **required** (do not reuse Aura Fresh DB) |
| `JWT_SECRET` | JWT signing key (32+ chars) — **required** |
| `STRIPE_API_KEY` | Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret |
| `APP_FRONTEND_BASE_URL` | Pixel Tech frontend origin (default `http://localhost:3000`) |
| `CLOUDINARY_*` | Product image uploads |
| `ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD` | Optional first admin on startup |

## Run with Docker + Neon

```bash
docker compose --env-file .env up --build
```

API base: **http://localhost:8081/api**  
Health: **http://localhost:8081/actuator/health**

Aura Fresh API stays on **8080**; Pixel Tech uses host port **8081**.

Stop:

```bash
docker compose down
```

## Optional: Maven (local JVM)

```bash
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run
```

Default local port is still `8080` inside the process; Docker maps it to **8081**. For a bare `mvnw` run alongside Aura Fresh, set `PORT=8081`.

## Google Cloud Run deploy

```bash
export GCP_PROJECT_ID=your-project-id
./scripts/deploy-cloud-run.sh
```

See **[DEPLOY.md](DEPLOY.md)** for env vars, IAM, and Stripe webhook setup.

## Tests

```bash
./mvnw test
```

## Pair with frontend

| App | Folder | Port |
|-----|--------|------|
| Pixel Tech web | `../pixel_tech_web` | 3000 |
| Pixel Tech API | `pixel_tech_backend` | 8081 |

## Tech stack

- Spring Boot 4.1 (Web, Data JPA, Actuator)
- Docker + Neon PostgreSQL
- JWT (jjwt), Stripe Java SDK, Cloudinary, Lombok

## License

MIT — see [LICENSE](LICENSE).
