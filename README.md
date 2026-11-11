# Instakill

Instakill is a Spring Boot 3 / Vue 3 starter for an Instagram-style social feed. The project starts as a monolith but is structured for future extraction into independent services.

## Project layout

- `backend/` – Spring Boot application exposing REST and MVC endpoints, backed by SQLite.
- `frontend/` – Vue 3 SPA that consumes the REST API.
- `scripts/` – helper tooling including Gradle bootstrap.

## First run

Make sure you have Java 23 installed. 

```bash
# From the project root
./scripts/bootstrap-gradle.sh
./backend/gradlew bootRun
```

On Windows PowerShell:

```powershell
./scripts/bootstrap-gradle.ps1
./backend/gradlew.bat bootRun
```

The bootstrap downloads Gradle 8.9 and generates a local wrapper (ignored by git). Subsequent builds can use the wrapper directly.

## Useful endpoints

- Web feed: http://localhost:8080/feed
- Login page: http://localhost:8080/auth/login
- REST OpenAPI UI: http://localhost:8080/api/swagger
- Health: http://localhost:8080/actuator/health
- Media upload: `POST /api/media` (multipart field `file`, authenticated) returns `{"mediaUrl": "/media/<uuid>.<ext>"}`; files are stored in `media.base-path` (default `./media`) and served from `/media/**`

## Post notifications

Every new post triggers an asynchronous HTTP request to a public [ntfy.sh](https://ntfy.sh) topic with the username, post ID and timestamp. The notification is sent after the post is committed, on a background thread, so it never slows down or breaks post creation. Subscribe by opening `https://ntfy.sh/<topic>` (or the ntfy app). Configure it in `application.yaml` under `notification.ntfy` (`enabled`, `base-url`, `topic`); the topic can also be set with the `NTFY_TOPIC` environment variable. Topics on ntfy.sh are public, so choose a unique name.

## Image processing

Uploaded `jpg`, `jpeg`, `png` and `gif` images are processed before saving: scaled down to at most 300 px wide (aspect ratio kept, small images are not enlarged) and stamped with an "Instakill Demo" watermark in the bottom-right corner. Settings live under `media.processing`. The logic is isolated in `com.instakill.media.processing` behind the `ImageProcessor` interface (bytes in, bytes out), so it can later move to a separate service or background worker. GIFs are saved as a single frame; videos and `webp` files are stored unchanged.

## Authentication

Authentication uses JWT stored in an `ACCESS_TOKEN` HttpOnly cookie. REST clients can authenticate by capturing the cookie returned from `/api/auth/login` or by sending the token manually.

Seed accounts (development profile):

| Username | Email             | Password |
|----------|-------------------|----------|
| alice    | alice@example.com | password |
| bob      | bob@example.com   | password |

## Running tests

```bash
cd backend
./gradlew test
```

The tests cover feed ranking, like toggling, MVC flows, and an end-to-end REST happy-path.

## Frontend

The Vue application lives in `frontend/`. Install dependencies and start the dev server with:

```bash
cd frontend
npm install
npm run dev
```

With default settings, you will be able to access the frontend on `http://localhost:5173/` 
It assumes the backend is running at `http://localhost:8080`.
Check `vite.config.js` for these settings.
