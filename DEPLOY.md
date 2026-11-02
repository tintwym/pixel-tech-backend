# Production deployment (Render)

Use this after pushing the backend repo to GitHub. **Redeploy** (or sync env) when variables change.

## Repository layout

| Path | Role |
|------|------|
| `LICENSE`, `README.md`, `DEPLOY.md` | Repo docs |
| `Dockerfile`, `.dockerignore` | API Docker image |
| `render.yaml` | Render Blueprint (web service) |

The Pixel Tech frontend lives in `../web/` (admin in `../admin/`) — set `APP_FRONTEND_BASE_URL` to your deployed frontend URL.

Database: **[Neon](https://neon.tech)** PostgreSQL (external — not hosted on Render).

## Prerequisites

1. [Render](https://render.com) account  
2. Backend repo on GitHub  
3. Local `backend/.env` with Neon / Stripe / JWT / Cloudinary (for reference when filling the dashboard)

## Deploy API to Render

### Option A — Blueprint (`render.yaml`)

1. Render Dashboard → **New** → **Blueprint**  
2. Select the backend GitHub repo (root must contain `render.yaml` + `Dockerfile`)  
3. Confirm service `pixel-tech-api`  
4. Fill every `sync: false` env var in the dashboard (copy from your local `.env`)  
5. Deploy

### Option B — Manual Web Service

1. **New** → **Web Service** → this repo  
2. Runtime: **Docker**  
3. Region: **Singapore** (or closest)  
4. Instance: **Free** (spins down when idle) or **Starter** for always-on  
5. Health check path: `/actuator/health`  
6. Add env vars (table below) → **Create Web Service**

Copy the service URL, e.g. `https://pixel-tech-api.onrender.com`.

## Environment variables (Render)

**Dashboard:** Service → **Environment**

| Variable | Example / notes |
|----------|-----------------|
| `DATABASE_URL` | Neon `postgresql://…?sslmode=require` |
| `JWT_SECRET` | `openssl rand -base64 32` |
| `STRIPE_API_KEY` | `sk_test_…` or live key |
| `STRIPE_WEBHOOK_SECRET` | From Stripe → Webhooks (`whsec_…`) |
| `APP_FRONTEND_BASE_URL` | `https://your-app.vercel.app` |
| `APP_CHECKOUT_SUCCESS_URL` | `https://your-app.vercel.app/payment/success?session_id={CHECKOUT_SESSION_ID}` |
| `APP_CHECKOUT_CANCEL_URL` | `https://your-app.vercel.app/cart` |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | API key |
| `CLOUDINARY_API_SECRET` | API secret |
| `CLOUDINARY_FOLDER` | `pixel-tech/products` |
| `ADMIN_SEED_USERNAME` | Optional first admin |
| `ADMIN_SEED_PASSWORD` | Optional admin password |
| `GEMINI_API_KEY` | Optional — AI features |
| `GEMINI_MODEL` | Optional |

Render sets `PORT` automatically — do not hardcode it (`server.port=${PORT:8080}`).

## Frontend (Pixel Tech)

After deploy, point the frontends at this API:

1. On **Vercel** (web + admin), set `API_PROXY_TARGET` to the Render URL (**no** `/api` suffix, no trailing slash)  
2. Set `APP_FRONTEND_BASE_URL` on Render to your Vercel origin (e.g. `https://pixel-tech-mm.vercel.app`)  
3. Redeploy / restart the Render service after env changes  

## Stripe webhook

1. Stripe Dashboard → Developers → Webhooks → Add endpoint  
2. URL: `https://YOUR-SERVICE.onrender.com/api/stripe/webhook`  
3. Event: `checkout.session.completed`  
4. Copy signing secret → `STRIPE_WEBHOOK_SECRET` on Render → save (triggers redeploy)  

## Verify API

```bash
curl https://YOUR-SERVICE.onrender.com/actuator/health
curl https://YOUR-SERVICE.onrender.com/api/products/index
```

Expect JSON from `/api/products/index`, not HTML.

> **Free plan:** the service sleeps after idle. First request after sleep can take 30–60s.

## Admin catalog

1. Set `ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD` once, or promote a user in Neon:

   ```sql
   UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'Admin') WHERE username = 'youruser';
   ```

2. Sign in on the admin app → manage products  

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| CORS error | `APP_FRONTEND_BASE_URL` must match your Vercel origin exactly |
| `DATABASE_URL is not set` on startup | Add `DATABASE_URL` in Render → Environment |
| Cold start slow / 502 on first hit | Free tier sleep — wait and retry, or upgrade to Starter |
| Build fails | Run `docker build .` locally; ensure repo root has `Dockerfile` |
| Container failed to start | Check Render logs; usually missing `DATABASE_URL` or `JWT_SECRET` |
